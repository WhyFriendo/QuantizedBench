"""Convert google/gemma-3-270m-it into the same MLC quant variants already
used for gemma-3-1b-it (q4bf16_0, q4bf16_1, q4f32_1, q4f16_1), then register
the results in mlc-package-config.json.

Usage (from anywhere, paths below are absolute):
    python export_gemma_270m.py
"""

import json
import subprocess
import sys
from pathlib import Path

MLC_LLM_SOURCE_DIR = Path(r"C:\Mlc-app\mlc-llm")
PACKAGE_CONFIG_PATH = Path(__file__).resolve().parent / "mlc-package-config.json"

# Kept outside MLCChat/dist on purpose: `mlc_llm package` uses a `dist/`
# folder of its own inside MLCChat for build output, so a same-named
# folder here would collide with it.
OUTPUT_BASE_DIR = MLC_LLM_SOURCE_DIR / "custom_models"
SOURCE_MODEL_DIR = MLC_LLM_SOURCE_DIR / "custom_models" / "gemma-3-270m-it"

HF_REPO_ID = "google/gemma-3-270m-it"
CONV_TEMPLATE = "gemma3_instruction"
QUANTIZATIONS = ["q4bf16_0", "q4bf16_1", "q4f32_1", "q4f16_1"]

# gemma-3-1b-it uses this flat estimate across all four quants in the
# existing config; scaled down roughly by param count for the 270M model.
ESTIMATED_VRAM_BYTES = 600_000_000

OVERRIDES = {"prefill_chunk_size": 128, "context_window_size": 2048}


def run(cmd, cwd=None, success_marker=None):
    print(f"\n$ {' '.join(str(c) for c in cmd)}")
    result = subprocess.run(cmd, cwd=cwd, check=False)
    if result.returncode != 0:
        if success_marker is not None and success_marker.is_file():
            # mlc_llm/TVM sometimes access-violates on Windows during native
            # library teardown *after* it already wrote its output (exit code
            # 3221225477 / 0xC0000005). The work is done; only raise if the
            # expected output is actually missing.
            print(
                f"\nProcess exited with code {result.returncode}, but "
                f"{success_marker} was written, so treating this as a harmless "
                "crash-on-exit and continuing."
            )
        else:
            raise subprocess.CalledProcessError(result.returncode, cmd)


def ensure_source_model():
    if (SOURCE_MODEL_DIR / "config.json").is_file():
        print(f"Source weights already present at {SOURCE_MODEL_DIR}, skipping download.")
        return

    from huggingface_hub import snapshot_download
    from huggingface_hub.errors import GatedRepoError, LocalTokenNotFoundError

    OUTPUT_BASE_DIR.mkdir(parents=True, exist_ok=True)
    print(
        "google/gemma-3-270m-it is a gated model. Make sure you've accepted its "
        "license at https://huggingface.co/google/gemma-3-270m-it and are logged "
        "in (`hf auth login`) first."
    )
    try:
        snapshot_download(repo_id=HF_REPO_ID, local_dir=str(SOURCE_MODEL_DIR))
    except (GatedRepoError, LocalTokenNotFoundError) as exc:
        print(
            "\nDownload failed: you're not logged in or haven't accepted the license "
            "yet. Run `hf auth login`, accept the license on the model page, then "
            "re-run this script."
        )
        raise SystemExit(1) from exc


def convert_and_configure(quant):
    output_dir = OUTPUT_BASE_DIR / f"gemma-3-270m-it-{quant}-MLC"
    if (output_dir / "mlc-chat-config.json").is_file():
        print(f"\n[{quant}] already converted at {output_dir}, skipping.")
        return output_dir

    print(f"\n=== Converting {quant} ===")
    if (output_dir / "tensor-cache.json").is_file():
        print(f"[{quant}] weights already converted, skipping convert_weight.")
    else:
        run(
            [
                sys.executable,
                "-m",
                "mlc_llm",
                "convert_weight",
                str(SOURCE_MODEL_DIR),
                "--quantization",
                quant,
                "-o",
                str(output_dir),
            ],
            cwd=MLC_LLM_SOURCE_DIR,
            success_marker=output_dir / "tensor-cache.json",
        )
    run(
        [
            sys.executable,
            "-m",
            "mlc_llm",
            "gen_config",
            str(SOURCE_MODEL_DIR),
            "--quantization",
            quant,
            "--conv-template",
            CONV_TEMPLATE,
            "-o",
            str(output_dir),
        ],
        cwd=MLC_LLM_SOURCE_DIR,
        success_marker=output_dir / "mlc-chat-config.json",
    )
    return output_dir


def update_package_config(output_dirs_by_quant):
    with PACKAGE_CONFIG_PATH.open(encoding="utf-8") as f:
        data = json.load(f)

    existing_ids = {m["model_id"] for m in data["model_list"]}
    added = []
    for quant, output_dir in output_dirs_by_quant.items():
        model_id = f"gemma-3-270m-it-{quant}-MLC"
        if model_id in existing_ids:
            print(f"{model_id} already in model_list, skipping.")
            continue
        data["model_list"].append(
            {
                "model": output_dir.as_posix(),
                "model_id": model_id,
                "bundle_weight": True,
                "estimated_vram_bytes": ESTIMATED_VRAM_BYTES,
                "overrides": OVERRIDES,
            }
        )
        added.append(model_id)

    if added:
        with PACKAGE_CONFIG_PATH.open("w", encoding="utf-8") as f:
            json.dump(data, f, indent=4)
            f.write("\n")
        print(f"\nAdded {len(added)} entries to {PACKAGE_CONFIG_PATH}:")
        for model_id in added:
            print(f" - {model_id}")
    else:
        print("\nNo new entries added (all already present).")


def main():
    ensure_source_model()

    output_dirs_by_quant = {}
    for quant in QUANTIZATIONS:
        output_dirs_by_quant[quant] = convert_and_configure(quant)

    update_package_config(output_dirs_by_quant)
    print("\nDone. Re-run `python -m mlc_llm package` from MLCChat/ to pick these up.")


if __name__ == "__main__":
    main()
