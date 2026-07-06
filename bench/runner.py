from __future__ import annotations

import argparse
from pathlib import Path

from bench.config import iter_runs, load_config
from bench.run_eval import run_benchmark


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Quantized LLM benchmark runner")
    parser.add_argument(
        "--config",
        type=Path,
        default=Path("bench/config.yaml"),
        help="Path to benchmark config",
    )
    parser.add_argument("--model", action="append", help="Filter by model id")
    parser.add_argument(
        "--quantization",
        action="append",
        help="Filter by quantization name",
    )
    parser.add_argument(
        "--list",
        action="store_true",
        help="List planned runs without executing",
    )
    parser.add_argument(
        "--backend",
        choices=["mlc", "llama_cpp"],
        help="Filter by backend",
    )
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    config = load_config(args.config)
    runs = iter_runs(
        config,
        model_ids=args.model,
        quant_names=args.quantization,
        backend=args.backend,
    )

    for item in runs:
        model = item["model"]
        quant = item["quant"]
        tasks = quant.tasks if quant.tasks else model.tasks
        print(
            f"{model.id} | {quant.name} | backend={quant.backend} | tasks={','.join(tasks)}"
        )

    if args.list:
        return 0

    for item in runs:
        run_benchmark(config=config, model=item["model"], quant=item["quant"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
