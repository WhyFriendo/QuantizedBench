#!/usr/bin/env python3
"""Regenerate bench/config.yaml from bench/models_manifest.json.

Run this whenever the manifest changes so the config stays in sync with it.
"""

from __future__ import annotations

import json
from pathlib import Path

import yaml

REPO_ROOT = Path(__file__).resolve().parent.parent
MANIFEST_PATH = REPO_ROOT / "bench" / "models_manifest.json"
CONFIG_PATH = REPO_ROOT / "bench" / "config.yaml"

CONTEXT_SIZE = 4096
N_GPU_LAYERS = 99

# Approximate parameter count (billions), used to run smallest models first
# and leave the slowest/biggest for last.
MODEL_SIZE_BILLIONS = {
    "gemma3_270m": 0.27,
    "qwen3_0_6b": 0.6,
    "gemma3_1b": 1.0,
    "llama32_1b": 1.0,
    "qwen3_1_7b": 1.7,
    "llama32_3b": 3.0,
    "phi4_mini": 3.8,
    "gemma3_4b": 4.0,
    "qwen3_4b": 4.0,
    "llama31_8b": 8.0,
    "qwen3_8b": 8.0,
}


def build_config(manifest: dict) -> dict:
    models = []
    ordered_ids = sorted(
        manifest.keys(), key=lambda mid: (MODEL_SIZE_BILLIONS.get(mid, 999), mid)
    )
    for model_id in ordered_ids:
        info = manifest[model_id]
        quantizations = []
        for row in info["rows"]:
            quantizations.append(
                {
                    "name": row["quant_name"],
                    "backend": "llama_cpp",
                    "model_path": f"./gguf_models/{row['filename']}",
                    "n_gpu_layers": N_GPU_LAYERS,
                    "context_size": CONTEXT_SIZE,
                    "tasks": ["tinyBenchmarks"],
                    "num_fewshot": 0,
                    "limit": None,
                }
            )
        models.append(
            {
                "id": model_id,
                "display_name": info["family"],
                "tasks": [],
                "quantizations": quantizations,
            }
        )

    return {
        "models": models,
        "lm_eval": {
            "harness_path": "./lm-evaluation-harness",
            "batch_size": 4,
            "python": "python3",
            "num_concurrent": 1,
        },
    }


def main() -> None:
    manifest = json.loads(MANIFEST_PATH.read_text())
    config = build_config(manifest)
    with CONFIG_PATH.open("w") as fh:
        yaml.dump(config, fh, sort_keys=False, default_flow_style=False, width=100)

    total_quants = sum(len(m["quantizations"]) for m in config["models"])
    print(f"Wrote {CONFIG_PATH} — {len(config['models'])} models, {total_quants} quantizations.")


if __name__ == "__main__":
    main()
