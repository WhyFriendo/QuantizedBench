#!/usr/bin/env python3
"""Compare the local GGUF scorer with exact llama-server token scoring.

Start llama-server separately, then run, for example:
    python scripts/validate_gguf_scoring.py --base-url http://127.0.0.1:8080 \
        --questions 20 --output reports/scoring_validation.json

Use --arrow PATH to read a cached tinyMMLU test Arrow file without hub access.
This script requires the project's patched lm-evaluation-harness checkout.
"""

from __future__ import annotations

import argparse
import json
import statistics
import sys
from pathlib import Path

import requests
from datasets import Dataset, load_dataset

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "lm-evaluation-harness"))
from lm_eval.models.gguf import GGUFLM  # noqa: E402


class TracedGGUFLM(GGUFLM):
    def _next_token_candidates(self, prompt_tokens, retries=3, delay=5):
        candidates = super()._next_token_candidates(prompt_tokens, retries, delay)
        self.candidate_trace.append(candidates)
        return candidates


def post(base_url: str, path: str, payload: dict) -> dict:
    response = requests.post(f"{base_url}{path}", json=payload, timeout=120)
    response.raise_for_status()
    return response.json()


def tokenize(base_url: str, text: str, *, add_special: bool) -> list[int]:
    return post(base_url, "/tokenize", {"content": text, "add_special": add_special})[
        "tokens"
    ]


def exact_score(base_url: str, context_ids: list[int], target_ids: list[int]) -> float:
    total = 0.0
    for position, target_id in enumerate(target_ids):
        response = post(
            base_url,
            "/v1/completions",
            {
                "prompt": context_ids + target_ids[:position],
                "temperature": 0,
                "max_tokens": 1,
                "logprobs": 2,
                "logit_bias": [[target_id, 100]],
            },
        )
        entries = response["choices"][0]["logprobs"]["content"]
        if not entries or entries[0]["id"] != target_id:
            raise RuntimeError(f"Server did not return forced token {target_id}: {entries}")
        total += entries[0]["logprob"]
    return total


def load_questions(task: str, arrow: Path | None, count: int) -> Dataset:
    dataset_id, config = {
        "tinyMMLU": ("tinyBenchmarks/tinyMMLU", "all"),
        "tinyArc": ("tinyBenchmarks/tinyAI2_arc", "ARC-Challenge"),
    }[task]
    dataset = (
        Dataset.from_file(str(arrow))
        if arrow is not None
        else load_dataset(dataset_id, config, split="test")
    )
    return dataset.select(range(min(count, len(dataset))))


def question_parts(task: str, doc: dict) -> tuple[str, list[str], str]:
    if task == "tinyMMLU":
        return doc["input_formatted"], list("ABCD"), "ABCD"[doc["answer"]]
    choices = doc["choices"]["text"]
    labels = doc["choices"]["label"]
    return f"Question: {doc['question']}\nAnswer:", choices, choices[labels.index(doc["answerKey"])]


def validate(base_url: str, dataset: Dataset, task: str) -> dict:
    model = TracedGGUFLM(base_url=base_url)
    details = []
    score_errors = []
    missing_tokens = 0
    scored_tokens = 0
    boundary_mismatches = 0
    changed_answers = 0
    old_correct = 0
    exact_correct = 0
    old_correctness = []
    exact_correctness = []

    for question_id, doc in enumerate(dataset):
        context, choice_texts, target = question_parts(task, doc)
        choices = []
        for choice_text in choice_texts:
            continuation = f" {choice_text}"  # harness default target delimiter
            model.candidate_trace = []
            old_score, _ = model._score_continuation(context, continuation)
            old_context_ids = model._tokenize(context)
            old_target_ids = model._tokenize(continuation)
            misses = sum(
                token_id not in {candidate["id"] for candidate in candidates}
                for token_id, candidates in zip(old_target_ids, model.candidate_trace)
            )
            exact_separate = exact_score(base_url, old_context_ids, old_target_ids)

            # Also check whether separate tokenization matches the causal LM's
            # joint context + continuation tokenization at this boundary.
            joint_context_ids = tokenize(base_url, context, add_special=True)
            joint_all_ids = tokenize(base_url, context + continuation, add_special=True)
            joint_target_ids = joint_all_ids[len(joint_context_ids) :]
            boundary_mismatch = (
                old_context_ids + old_target_ids != joint_all_ids
                or joint_context_ids != old_context_ids
                or joint_target_ids != old_target_ids
            )
            if boundary_mismatch:
                boundary_mismatches += 1
                exact = exact_score(base_url, joint_context_ids, joint_target_ids)
            else:
                exact = exact_separate

            missing_tokens += misses
            scored_tokens += len(old_target_ids)
            score_errors.append(old_score - exact)
            choices.append(
                {
                    "choice": choice_text,
                    "patched_logprob": old_score,
                    "exact_logprob": exact,
                    "exact_separate_logprob": exact_separate,
                    "top20_misses": misses,
                    "token_boundary_mismatch": boundary_mismatch,
                }
            )

        def metric_score(item: dict, field: str) -> float:
            score = item[field]
            return score / len(item["choice"]) if task == "tinyArc" else score

        old_answer = max(choices, key=lambda item: metric_score(item, "patched_logprob"))[
            "choice"
        ]
        exact_answer = max(choices, key=lambda item: metric_score(item, "exact_logprob"))[
            "choice"
        ]
        changed_answers += old_answer != exact_answer
        old_hit = int(old_answer == target)
        exact_hit = int(exact_answer == target)
        old_correct += old_hit
        exact_correct += exact_hit
        old_correctness.append(old_hit)
        exact_correctness.append(exact_hit)
        details.append(
            {
                "question_id": question_id,
                "target": target,
                "patched_answer": old_answer,
                "exact_answer": exact_answer,
                "choices": choices,
            }
        )
        print(
            f"{question_id + 1}/{len(dataset)} target={target} "
            f"patched={old_answer} exact={exact_answer} "
            f"top20_misses={sum(c['top20_misses'] for c in choices)}",
            flush=True,
        )

    report = {
        "model_adapter": str(Path(sys.modules[GGUFLM.__module__].__file__).resolve()),
        "dataset": f"{task} test",
        "questions": len(dataset),
        "choices": sum(len(question["choices"]) for question in details),
        "tokens_scored": scored_tokens,
        "top20_misses": missing_tokens,
        "token_boundary_mismatches": boundary_mismatches,
        "changed_answers": changed_answers,
        "patched_correct": old_correct,
        "exact_correct": exact_correct,
        "mean_logprob_error": statistics.mean(score_errors),
        "max_absolute_logprob_error": max(map(abs, score_errors)),
        "details": details,
    }
    if len(dataset) == 100:
        import numpy as np
        import tinyBenchmarks as tb

        benchmark = {"tinyMMLU": "mmlu", "tinyArc": "arc"}[task]
        report["patched_gpirt"] = float(
            tb.evaluate(np.array(old_correctness), benchmark)[benchmark]["gpirt"]
        )
        report["exact_gpirt"] = float(
            tb.evaluate(np.array(exact_correctness), benchmark)[benchmark]["gpirt"]
        )
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--task", choices=["tinyMMLU", "tinyArc"], default="tinyMMLU")
    parser.add_argument("--arrow", type=Path)
    parser.add_argument("--questions", type=int, default=20)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.questions < 1:
        parser.error("--questions must be positive")
    dataset = load_questions(args.task, args.arrow, args.questions)
    report = validate(args.base_url.rstrip("/"), dataset, args.task)
    summary = {key: value for key, value in report.items() if key != "details"}
    print(json.dumps(summary, indent=2))
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
