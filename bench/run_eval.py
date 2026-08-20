from __future__ import annotations

import csv
import json
import os
import subprocess
import time
from dataclasses import asdict
from pathlib import Path
from typing import Dict

from bench.backends.llama_cpp import start_llama_cpp
from bench.backends.mlc import start_mlc
from bench.config import BenchConfig, ModelConfig, QuantizationConfig
from bench.utils import Endpoint, ensure_dir, wait_for_port


def run_benchmark(*, config: BenchConfig, model: ModelConfig, quant: QuantizationConfig) -> None:
    results_dir = Path("results") / model.id / quant.name
    ensure_dir(results_dir)

    endpoint = _allocate_endpoint(quant)
    handle = _start_backend(config, quant, endpoint, results_dir=results_dir)
    try:
        wait_for_port(endpoint.host, endpoint.port, timeout_s=60.0)
        output_path = results_dir / "lm_eval.json"
        merged_rows = _run_lm_eval(
            config=config,
            model=model,
            quant=quant,
            endpoint=endpoint,
            output_path=output_path,
        )
        if merged_rows:
            _write_merged_summary_csv(results_dir, merged_rows)
        _write_metadata(results_dir / "meta.json", model, quant, endpoint)
    finally:
        handle.stop()


def _allocate_endpoint(quant: QuantizationConfig) -> Endpoint:
    if quant.backend == "llama_cpp":
        return Endpoint(host="127.0.0.1", port=8080)
    return Endpoint(host="127.0.0.1", port=8000)


def _start_backend(
    config: BenchConfig,
    quant: QuantizationConfig,
    endpoint: Endpoint,
    *,
    results_dir: Path | None = None,
):
    if quant.backend == "llama_cpp":
        return start_llama_cpp(
            model_path=quant.model_path,
            host=endpoint.host,
            port=endpoint.port,
            n_gpu_layers=quant.n_gpu_layers if quant.n_gpu_layers is not None else 99,
            context_size=quant.context_size or 4096,
            extra_args=quant.extra_args or [],
        )
    # MLC backend — always use local mlc_llm serve via conda env.
    mlc_python = quant.mlc_python or config.lm_eval.mlc_python
    if not mlc_python:
        raise ValueError(
            f"No mlc_python specified for quantization '{quant.name}'. "
            "Set it per-quantization or globally under lm_eval.mlc_python."
        )
    return start_mlc(
        python=Path(mlc_python),
        model_uri=quant.model_uri,
        model_lib=quant.model_lib,
        device=quant.device or "cuda",
        host=endpoint.host,
        port=endpoint.port,
        log_dir=results_dir,
    )


def _run_lm_eval(
    *,
    config: BenchConfig,
    model: ModelConfig,
    quant: QuantizationConfig,
    endpoint: Endpoint,
    output_path: Path,
) -> List[Dict[str, object]]:
    harness = config.lm_eval.harness_path
    tasks = quant.tasks if quant.tasks else model.tasks
    merged_rows: List[Dict[str, object]] = []
    for task in tasks:
        task_rows = _run_single_lm_eval(
            config=config,
            task=task,
            quant=quant,
            endpoint=endpoint,
            output_path=output_path,
        )
        merged_rows.extend(task_rows)
    return merged_rows


def _resolve_lm_eval_model(quant: QuantizationConfig) -> str:
    if quant.backend == "llama_cpp":
        return "gguf"
    return "local-chat-completions"


def _build_model_args(quant: QuantizationConfig, endpoint: Endpoint, num_concurrent: int) -> str:
    if quant.backend == "llama_cpp":
        return f"base_url={endpoint.base_url}"
    base = (
        f"base_url={endpoint.chat_url},model={quant.model_uri},eos_string=</s>,"
        "max_gen_toks=256,tokenized_requests=False,max_retries=0"
    )
    if num_concurrent > 1:
        base += f",num_concurrent={num_concurrent}"
    return base


def _run_single_lm_eval(
    *,
    config: BenchConfig,
    task: str,
    quant: QuantizationConfig,
    endpoint: Endpoint,
    output_path: Path,
) -> List[Dict[str, object]]:
    harness = config.lm_eval.harness_path
    task_output = output_path.parent / f"{output_path.stem}_{task}.json"
    cmd = [
        config.lm_eval.python,
        "-m",
        "lm_eval",
        "run",
        "--model",
        _resolve_lm_eval_model(quant),
        "--model_args",
        _build_model_args(quant, endpoint, config.lm_eval.num_concurrent),
        "--tasks",
        task,
        "--batch_size",
        str(config.lm_eval.batch_size),
        "--output_path",
        str(task_output),
    ]
    if quant.num_fewshot is not None:
        cmd.extend(["--num_fewshot", str(quant.num_fewshot)])
    elif config.lm_eval.num_fewshot is not None:
        cmd.extend(["--num_fewshot", str(config.lm_eval.num_fewshot)])
    if quant.limit is not None:
        cmd.extend(["--limit", str(quant.limit)])
    elif config.lm_eval.limit is not None:
        cmd.extend(["--limit", str(config.lm_eval.limit)])
    if quant.backend == "mlc":
        cmd.append("--apply_chat_template")
    env = os.environ.copy()
    env.setdefault("OPENAI_API_KEY", "dummy")
    subprocess.run(cmd, check=True, cwd=str(harness), env=env)
    return _write_task_summary(
        harness=Path(harness),
        task_output=task_output,
        results_dir=output_path.parent,
        task=task,
    )


def _write_metadata(path: Path, model: ModelConfig, quant: QuantizationConfig, endpoint: Endpoint) -> None:
    payload = {
        "model": _asdict_clean(model),
        "quantization": _asdict_clean(quant),
        "endpoint": _asdict_clean(endpoint),
    }
    path.write_text(json.dumps(payload, indent=2, sort_keys=True), encoding="utf-8")


def _asdict_clean(obj) -> Dict[str, object]:
    raw = asdict(obj)
    return {key: value for key, value in raw.items() if value is not None}


def _write_task_summary(
    *,
    harness: Path,
    task_output: Path,
    results_dir: Path,
    task: str,
) -> List[Dict[str, object]]:
    task_json_path = harness / task_output
    if not task_json_path.exists():
        if task_output.exists():
            task_json_path = task_output
        else:
            # lm-eval may append a timestamp to output filenames.
            task_output_dir = harness / task_output.parent
            pattern = f"{task_output.stem}_*.json"
            candidates = sorted(task_output_dir.glob(pattern), key=lambda p: p.stat().st_mtime)
            if not candidates:
                return []
            task_json_path = candidates[-1]
    rows = _parse_task_rows(task_json_path, task)
    if not rows:
        return []

    version = rows[0]["version"]
    n_shot = rows[0]["n_shot"]
    rows.sort(key=lambda row: (row["filter"], row["metric"]))
    summary_path = results_dir / f"{task_output.stem}_summary.md"
    lines = [
        "|Task|Version|Filter|n-shot|Metric|Value|Stderr|",
        "|-----|------:|------|-----:|------|-----:|-----:|",
    ]
    for row in rows:
        lines.append(
            f"|{row['task']}|{row['version']}|{row['filter']}|{row['n_shot']}|"
            f"{row['metric']}|{row['value']}|{row['stderr']}|"
        )
    summary_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    csv_path = results_dir / f"{task_output.stem}_summary.csv"
    with csv_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["task", "version", "filter", "n_shot", "metric", "value", "stderr"])
        for row in rows:
            writer.writerow(
                [
                    row["task"],
                    row["version"],
                    row["filter"],
                    row["n_shot"],
                    row["metric"],
                    row["value"],
                    row["stderr"],
                ]
            )
    return rows


def _parse_task_rows(task_json_path: Path, task: str) -> List[Dict[str, object]]:
    data = json.loads(task_json_path.read_text(encoding="utf-8"))
    results = data.get("results", {})
    rows: List[Dict[str, object]] = []

    def append_metric_rows(task_name: str, task_results: Dict[str, object]) -> None:
        version = data.get("versions", {}).get(task_name, "")
        n_shot = data.get("n-shot", {}).get(task_name, "")
        for key, value in task_results.items():
            if key in {"name", "alias", "sample_len"}:
                continue
            if "," in key:
                metric_key, filter_name = key.rsplit(",", 1)
            else:
                metric_key, filter_name = key, "none"
            if metric_key.endswith("_stderr"):
                continue
            stderr_key = (
                f"{metric_key}_stderr,{filter_name}"
                if "," in key
                else f"{metric_key}_stderr"
            )
            stderr = task_results.get(stderr_key, "N/A")
            rows.append(
                {
                    "task": task_name,
                    "version": version,
                    "filter": filter_name,
                    "n_shot": n_shot,
                    "metric": metric_key,
                    "value": value,
                    "stderr": stderr,
                }
            )

    task_results = results.get(task)
    if task_results:
        append_metric_rows(task, task_results)

    if not rows:
        for subtask in data.get("group_subtasks", {}).get(task, []):
            subtask_results = results.get(subtask)
            if subtask_results:
                append_metric_rows(subtask, subtask_results)

    if not rows and len(results) == 1:
        only_task_name, only_results = next(iter(results.items()))
        append_metric_rows(only_task_name, only_results)

    return rows


def _write_merged_summary_csv(results_dir: Path, rows: List[Dict[str, object]]) -> None:
    if not rows:
        return
    csv_path = results_dir / "lm_eval_summary.csv"
    rows_sorted = sorted(rows, key=lambda row: (row["task"], row["filter"], row["metric"]))
    with csv_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["task", "version", "filter", "n_shot", "metric", "value", "stderr"])
        for row in rows_sorted:
            writer.writerow(
                [
                    row["task"],
                    row["version"],
                    row["filter"],
                    row["n_shot"],
                    row["metric"],
                    row["value"],
                    row["stderr"],
                ]
            )
