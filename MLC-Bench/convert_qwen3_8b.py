"""Convert Qwen3-8B's q3f16_1 quant locally (q0bf16 intentionally left for
later — either here or on EC2, undecided yet, so the raw source checkpoint
is kept around afterward instead of being deleted).

Originally planned for EC2 due to RAM concerns, but huggingface_loader.py
turns out to stream per-shard rather than loading the whole checkpoint into
RAM at once (see _load_mlc_param / _load_file / _unload_file), so this
should be feasible on this machine's ~16GB RAM too.

Usage:
    set HF_TOKEN=hf_...
    python convert_qwen3_8b.py
"""

from convert_local_models import QWEN3_CONV_TEMPLATE, convert_and_configure, ensure_source_model

FAMILY = "Qwen3-8B"
REPO_ID = "Qwen/Qwen3-8B"
QUANTS = ["q3f16_1"]


def main():
    source_dir = ensure_source_model(FAMILY, REPO_ID)
    for quant in QUANTS:
        convert_and_configure(FAMILY, quant, source_dir, "chatml", QWEN3_CONV_TEMPLATE)

    print(f"\nDone converting {FAMILY} {QUANTS}. Source checkpoint kept at {source_dir} for now.")


if __name__ == "__main__":
    main()
