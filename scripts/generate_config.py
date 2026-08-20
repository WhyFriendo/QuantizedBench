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


def build_config(manifest: dict) -> dict:
    models = []
    for model_id, info in manifest.items():
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
