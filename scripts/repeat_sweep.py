#!/usr/bin/env python3
"""Run a resumable multi-quantization benchmark sweep with separate repeats."""

from __future__ import annotations

import argparse
import json
import os
import platform
import socket
import sys
import time
from dataclasses import asdict, replace
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from bench.config import iter_runs, load_config  # noqa: E402
from bench.run_eval import run_benchmark  # noqa: E402


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=ROOT / "bench/config.yaml")
    parser.add_argument("--model", default="gemma3_270m")
    parser.add_argument("--quantization", action="append")
    parser.add_argument("--repeats", type=int, default=5)
    parser.add_argument("--output-root", type=Path, required=True)
    args = parser.parse_args()
    if args.repeats < 1:
        parser.error("--repeats must be at least 1")

    output_root = args.output_root.expanduser().resolve()
    output_root.mkdir(parents=True, exist_ok=True)
    config = load_config(args.config.expanduser().resolve())
    config = replace(
        config,
        lm_eval=replace(
            config.lm_eval,
            harness_path=(ROOT / config.lm_eval.harness_path).resolve(),
            python=sys.executable,
        ),
    )
    selected = iter_runs(
        config,
        model_ids=[args.model],
        quant_names=args.quantization,
        backend="llama_cpp",
    )
    selected = [
        {
            "model": item["model"],
            "quant": replace(
                item["quant"],
                model_path=str((ROOT / item["quant"].model_path).resolve()),
            ),
        }
        for item in selected
    ]

    manifest = {
        "created_at": utc_now(),
        "hostname": socket.gethostname(),
        "platform": platform.platform(),
        "python": sys.version,
        "model": args.model,
        "repeats": args.repeats,
        "quantizations": [item["quant"].name for item in selected],
        "jobs": len(selected) * args.repeats,
        "lm_eval": asdict(config.lm_eval),
    }
    manifest["lm_eval"]["harness_path"] = str(manifest["lm_eval"]["harness_path"])
    (output_root / "sweep_manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )

    original_cwd = Path.cwd()
    try:
        for repeat in range(1, args.repeats + 1):
            repeat_root = output_root / f"repeat_{repeat:02d}"
            repeat_root.mkdir(exist_ok=True)
            for index, item in enumerate(selected, 1):
                model, quant = item["model"], item["quant"]
                result_dir = repeat_root / "results" / model.id / quant.name
                completed = result_dir / "COMPLETED"
                if completed.is_file():
                    print(
                        f"Skipping completed repeat {repeat}/{args.repeats}, "
                        f"quantization {index}/{len(selected)}: {quant.name}",
                        flush=True,
                    )
                    continue

                repeat_root.mkdir(exist_ok=True)
                os.chdir(repeat_root)
                started_at = utc_now()
                start = time.monotonic()
                print(
                    f"Starting repeat {repeat}/{args.repeats}, "
                    f"quantization {index}/{len(selected)}: {quant.name}",
                    flush=True,
                )
                try:
                    run_benchmark(config=config, model=model, quant=quant)
                    elapsed = time.monotonic() - start
                    summary = result_dir / "lm_eval_tinyBenchmarks_summary.csv"
                    raw_results = sorted(result_dir.glob("lm_eval_tinyBenchmarks_*.json"))
                    if not summary.is_file() or not raw_results:
                        raise RuntimeError(
                            f"Incomplete output for {quant.name}: "
                            f"summary={summary.is_file()}, raw_results={len(raw_results)}"
                        )
                    completed.write_text(
                        json.dumps(
                            {
                                "started_at": started_at,
                                "finished_at": utc_now(),
                                "elapsed_seconds": elapsed,
                                "raw_results": [path.name for path in raw_results],
                            },
                            indent=2,
                        )
                        + "\n",
                        encoding="utf-8",
                    )
                    (result_dir / "FAILED.txt").unlink(missing_ok=True)
                    print(f"Finished {quant.name} in {elapsed:.1f}s", flush=True)
                except Exception as exc:
                    result_dir.mkdir(parents=True, exist_ok=True)
                    (result_dir / "FAILED.txt").write_text(
                        f"{utc_now()}\n{type(exc).__name__}: {exc}\n", encoding="utf-8"
                    )
                    raise
    finally:
        os.chdir(original_cwd)

    (output_root / "SWEEP_COMPLETED").write_text(utc_now() + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
