#!/usr/bin/env python3
"""Repeat the configured Gemma 3 270M IQ3_XXS tinyBenchmarks run and compare scores."""

from __future__ import annotations

import csv
import argparse
import json
import os
import statistics
import sys
import time
from dataclasses import replace
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from bench.config import load_config  # noqa: E402
from bench.run_eval import run_benchmark  # noqa: E402

DEFAULT_OUTPUT = ROOT / "reports" / "gemma3_270m_iq3_xxs_repeats"


def read_scores(path: Path) -> dict[tuple[str, str, str], float]:
    with path.open(newline="", encoding="utf-8") as handle:
        return {
            (row["task"], row["filter"], row["metric"]): float(row["value"])
            for row in csv.DictReader(handle)
        }


def write_comparison(reference: Path, runs: list[Path], output_dir: Path) -> None:
    baseline = read_scores(reference)
    scores = [read_scores(path) for path in runs]
    if any(set(score) != set(baseline) for score in scores):
        raise ValueError("Score rows differ between the reference and repeats")
    output = output_dir / "comparison.csv"
    with output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(
            ["task", "filter", "metric", "reference", *[f"run_{i}" for i in range(1, len(runs) + 1)],
             "mean", "sample_sd", "mean_minus_reference"]
        )
        for key in sorted(baseline):
            values = [score[key] for score in scores]
            mean = statistics.mean(values)
            writer.writerow([
                *key, baseline[key], *values, mean, statistics.stdev(values),
                mean - baseline[key],
            ])
    print(f"Comparison written to {output}", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument(
        "--reference",
        type=Path,
        required=True,
        help="Reference lm_eval_tinyBenchmarks_summary.csv to compare against",
    )
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    if args.repeats < 2:
        raise ValueError("At least two repeats are needed for a sample standard deviation")
    config = load_config(ROOT / "bench" / "config.yaml")
    model = next(m for m in config.models if m.id == "gemma3_270m")
    quant = next(q for q in model.quantizations if q.name == "iq3_xxs")
    config = replace(
        config,
        lm_eval=replace(
            config.lm_eval,
            harness_path=(ROOT / config.lm_eval.harness_path).resolve(),
            python=sys.executable,
        ),
    )
    quant = replace(quant, model_path=str((ROOT / quant.model_path).resolve()))
    if quant.tasks != ["tinyBenchmarks"] or quant.num_fewshot != 0 or quant.limit is not None:
        raise ValueError("Configured tasks, shots, or limit changed")
    if not Path(quant.model_path).is_file():
        raise FileNotFoundError(quant.model_path)
    reference = args.reference.expanduser().resolve()
    output_dir = args.output.expanduser().resolve()
    if not reference.is_file():
        raise FileNotFoundError(reference)

    output_dir.mkdir(parents=True, exist_ok=True)
    original_cwd = Path.cwd()
    runs = []
    try:
        for i in range(1, args.repeats + 1):
            run_dir = output_dir / f"run_{i}"
            run_dir.mkdir(exist_ok=True)
            summary = run_dir / "results" / model.id / quant.name / "lm_eval_tinyBenchmarks_summary.csv"
            if not summary.exists():
                os.chdir(run_dir)
                print(f"Starting run {i}/{args.repeats} at {time.strftime('%Y-%m-%d %H:%M:%S')}", flush=True)
                start = time.monotonic()
                run_benchmark(config=config, model=model, quant=quant)
                print(f"Finished run {i}/{args.repeats} in {time.monotonic() - start:.1f}s", flush=True)
            else:
                print(f"Reusing completed run {i}/{args.repeats}: {summary}", flush=True)
            if not summary.is_file():
                raise RuntimeError(f"Missing summary for run {i}: {summary}")
            runs.append(summary)
    finally:
        os.chdir(original_cwd)
    write_comparison(reference, runs, output_dir)
    (output_dir / "manifest.json").write_text(json.dumps({
        "model": model.id,
        "quantization": quant.name,
        "model_path": quant.model_path,
        "reference": str(reference),
        "repeats": args.repeats,
        "task": "tinyBenchmarks",
        "num_fewshot": 0,
        "batch_size": config.lm_eval.batch_size,
        "context_size": quant.context_size,
        "n_gpu_layers_requested": quant.n_gpu_layers,
        "note": "Local machine has no GPU, so inference is on CPU.",
    }, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
