"""Convert the local batch of the weight-precision-scale model set
(q3f16_1 / q0bf16 for models small enough to convert on this machine;
q4f16_1 is skipped everywhere since it's already published by mlc-ai or,
for gemma-3-270m-it, already converted+uploaded in an earlier run).

Llama-3.1-8B-Instruct and Qwen3-8B are deliberately excluded here — at
~8B params they need more RAM than this machine's ~16GB to convert
safely; those run on a separate EC2-based script instead.

Usage:
    hf auth login   # or: set HF_TOKEN=hf_...  (needs license-accept on
                     # the gated google/* and meta-llama/* repos below)
    python convert_local_models.py
"""

import json
import subprocess
import sys
from pathlib import Path

MLC_LLM_SOURCE_DIR = Path(r"C:\Mlc-app\mlc-llm")

# Kept outside MLCChat/dist on purpose: `mlc_llm package` uses a `dist/`
# folder of its own inside MLCChat for build output, so a same-named
# folder here would collide with it.
OUTPUT_BASE_DIR = MLC_LLM_SOURCE_DIR / "custom_models"

# This installed mlc_llm's `gen_config --conv-template` choice-list predates
# "qwen3" being registered as a named template, even though mlc-ai's own
# published Qwen3 configs already use it (they were generated with a newer
# build). We generate with a structurally-close placeholder ("chatml" — same
# <|im_start|>/<|im_end|> format) and then overwrite conv_template in the
# resulting mlc-chat-config.json with the real object, copied verbatim from
# https://huggingface.co/mlc-ai/Qwen3-0.6B-q4f16_1-MLC/resolve/main/mlc-chat-config.json
QWEN3_CONV_TEMPLATE = {
    "name": "qwen3",
    "system_template": "<|im_start|>system\n{system_message}<|im_end|>\n",
    "system_message": "You are a helpful assistant.",
    "add_role_after_system_message": True,
    "roles": {"user": "<|im_start|>user", "assistant": "<|im_start|>assistant"},
    "role_templates": {
        "user": "{user_message}",
        "assistant": "{assistant_message}",
        "tool": "{tool_message}",
    },
    "messages": [],
    "seps": ["<|im_end|>\n"],
    "role_content_sep": "\n",
    "role_empty_sep": "\n",
    "stop_str": ["<|endoftext|>", "<|im_end|>"],
    "stop_token_ids": [151643, 151645],
    "strip_reasoning_in_history": True,
    "function_string": "",
    "use_function_calling": False,
}

# family -> (base HF repo, conv_template, conv_template_override)
# conv_template_override, when set, replaces conv_template in the generated
# mlc-chat-config.json after gen_config runs (see QWEN3_CONV_TEMPLATE above).
MODEL_REGISTRY = {
    "gemma-3-270m-it": ("google/gemma-3-270m-it", "gemma3_instruction", None),
    "gemma-3-1b-it": ("google/gemma-3-1b-it", "gemma3_instruction", None),
    "gemma-3-4b-it": ("google/gemma-3-4b-it", "gemma3_instruction", None),
    "Llama-3.2-3B-Instruct": ("meta-llama/Llama-3.2-3B-Instruct", "llama-3_1", None),
    "Llama-3.2-1B-Instruct": ("meta-llama/Llama-3.2-1B-Instruct", "llama-3_1", None),
    "Phi-4-mini-instruct": ("microsoft/Phi-4-mini-instruct", "phi-4", None),
    "Qwen3-0.6B": ("Qwen/Qwen3-0.6B", "chatml", QWEN3_CONV_TEMPLATE),
    "Qwen3-1.7B": ("Qwen/Qwen3-1.7B", "chatml", QWEN3_CONV_TEMPLATE),
    "Qwen3-4B": ("Qwen/Qwen3-4B", "chatml", QWEN3_CONV_TEMPLATE),
}

# (family, quantization) pairs to convert in this local batch
TASKS = [
    ("gemma-3-270m-it", "q3f16_1"),
    ("gemma-3-270m-it", "q0bf16"),
    ("gemma-3-1b-it", "q3f16_1"),
    ("gemma-3-4b-it", "q3f16_1"),
    ("Llama-3.2-3B-Instruct", "q3f16_1"),
    ("Llama-3.2-3B-Instruct", "q0bf16"),
    ("Llama-3.2-1B-Instruct", "q3f16_1"),
    ("Llama-3.2-1B-Instruct", "q0bf16"),
    ("Phi-4-mini-instruct", "q3f16_1"),
    ("Phi-4-mini-instruct", "q0bf16"),
    ("Qwen3-0.6B", "q3f16_1"),
    ("Qwen3-0.6B", "q0bf16"),
    ("Qwen3-1.7B", "q3f16_1"),
    ("Qwen3-1.7B", "q0bf16"),
    ("Qwen3-4B", "q3f16_1"),
    ("Qwen3-4B", "q0bf16"),
]


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


def ensure_source_model(family, repo_id):
    source_dir = OUTPUT_BASE_DIR / family

    from huggingface_hub import snapshot_download
    from huggingface_hub.errors import GatedRepoError, LocalTokenNotFoundError

    # Always call snapshot_download rather than trusting a "does config.json
    # exist" shortcut: that check previously treated a download that got cut
    # short (e.g. by running out of disk mid-transfer) as complete, since
    # config.json downloads early but the large weight shards hadn't arrived
    # yet. snapshot_download is itself idempotent/resumable, so re-calling it
    # is cheap when the model is genuinely already fully downloaded and
    # correctly fills in whatever's actually missing otherwise.
    OUTPUT_BASE_DIR.mkdir(parents=True, exist_ok=True)
    print(f"\n=== Ensuring {repo_id} is fully downloaded ===")
    try:
        snapshot_download(repo_id=repo_id, local_dir=str(source_dir))
    except (GatedRepoError, LocalTokenNotFoundError) as exc:
        print(
            f"\nDownload of {repo_id} failed: you're not logged in or haven't "
            f"accepted its license yet. Accept it at https://huggingface.co/{repo_id}, "
            "run `hf auth login` (or set HF_TOKEN), then re-run this script."
        )
        raise SystemExit(1) from exc
    return source_dir


def convert_and_configure(family, quant, source_dir, conv_template, conv_template_override):
    output_dir = OUTPUT_BASE_DIR / f"{family}-{quant}-MLC"
    if (output_dir / "mlc-chat-config.json").is_file():
        print(f"[{family} {quant}] already converted at {output_dir}, skipping.")
        return output_dir

    print(f"\n=== Converting {family} {quant} ===")
    if (output_dir / "tensor-cache.json").is_file():
        print(f"[{family} {quant}] weights already converted, skipping convert_weight.")
    else:
        run(
            [
                sys.executable,
                "-m",
                "mlc_llm",
                "convert_weight",
                str(source_dir),
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
            str(source_dir),
            "--quantization",
            quant,
            "--conv-template",
            conv_template,
            "-o",
            str(output_dir),
        ],
        cwd=MLC_LLM_SOURCE_DIR,
        success_marker=output_dir / "mlc-chat-config.json",
    )

    config_path = output_dir / "mlc-chat-config.json"
    with config_path.open(encoding="utf-8") as f:
        config = json.load(f)
    dirty = False

    if conv_template_override is not None:
        config["conv_template"] = conv_template_override
        dirty = True
        print(f"[{family} {quant}] patched conv_template with the real '{conv_template_override['name']}' template.")

    # gen_config sets active_vocab_size from len(hf_tokenizer), which for some
    # tokenizers (seen on gemma-3-270m-it/1b-it) counts a reserved special
    # token that has no real embedding row, exceeding vocab_size by one. That
    # causes an out-of-bounds read into the lm_head output at sampling time,
    # producing NaN. Clamp it defensively rather than trust the tool's output.
    active_vocab_size = config.get("active_vocab_size")
    vocab_size = config.get("vocab_size")
    if active_vocab_size is not None and vocab_size is not None and active_vocab_size > vocab_size:
        print(
            f"[{family} {quant}] active_vocab_size ({active_vocab_size}) exceeds "
            f"vocab_size ({vocab_size}), clamping to avoid out-of-bounds sampling."
        )
        config["active_vocab_size"] = vocab_size
        dirty = True

    if dirty:
        with config_path.open("w", encoding="utf-8") as f:
            json.dump(config, f, indent=2)

    return output_dir


def tasks_by_family():
    grouped = {}
    for family, quant in TASKS:
        grouped.setdefault(family, []).append(quant)
    return grouped


def all_tasks_done(family, quants):
    return all(
        (OUTPUT_BASE_DIR / f"{family}-{q}-MLC" / "mlc-chat-config.json").is_file() for q in quants
    )


def main():
    import shutil

    results = {}
    for family, quants in tasks_by_family().items():
        repo_id, conv_template, conv_template_override = MODEL_REGISTRY[family]

        if all_tasks_done(family, quants):
            print(f"\n[{family}] all {len(quants)} quant(s) already converted, skipping entirely.")
            for q in quants:
                results[(family, q)] = OUTPUT_BASE_DIR / f"{family}-{q}-MLC"
            continue

        source_dir = ensure_source_model(family, repo_id)
        for quant in quants:
            results[(family, quant)] = convert_and_configure(
                family, quant, source_dir, conv_template, conv_template_override
            )

        # Reclaim disk: the raw source checkpoint is only needed while
        # converting this family's quants, and can be re-downloaded later
        # if ever needed again.
        if source_dir.is_dir():
            print(f"[{family}] all quants done, deleting raw source checkpoint to free disk.")
            shutil.rmtree(source_dir)

    print(f"\nDone. Converted {len(results)} (family, quant) combinations under {OUTPUT_BASE_DIR}.")
    print("Next: upload these to Hugging Face, then update mlc-package-config.json.")


if __name__ == "__main__":
    main()
