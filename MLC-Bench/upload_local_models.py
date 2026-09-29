"""Upload the local conversion batch (see convert_local_models.py) to the
user's Hugging Face account as public repos, one per (family, quant),
named after the local output folders (e.g. Qwen3-4B-q3f16_1-MLC).

Usage:
    set HF_TOKEN=hf_...   # needs WRITE access
    python upload_local_models.py
"""

from huggingface_hub import HfApi
from huggingface_hub.errors import LocalTokenNotFoundError

from convert_local_models import OUTPUT_BASE_DIR, TASKS

PRIVATE = False  # user chose public repos (same as gemma-3-270m-it earlier)


def main():
    api = HfApi()
    try:
        username = api.whoami()["name"]
    except LocalTokenNotFoundError:
        print(
            "No Hugging Face token found. Set one with write access first:\n"
            "    set HF_TOKEN=hf_your_write_token_here"
        )
        raise SystemExit(1)

    for family, quant in TASKS:
        folder = OUTPUT_BASE_DIR / f"{family}-{quant}-MLC"
        if not (folder / "mlc-chat-config.json").is_file():
            print(f"Skipping {family} {quant}: {folder} isn't converted yet (run convert_local_models.py first).")
            continue

        repo_id = f"{username}/{folder.name}"
        print(f"\n=== {repo_id} ===")
        api.create_repo(repo_id=repo_id, repo_type="model", private=PRIVATE, exist_ok=True)
        api.upload_folder(
            folder_path=str(folder),
            repo_id=repo_id,
            repo_type="model",
            commit_message=f"Add {folder.name} quantized weights",
        )
        print(f"Uploaded: https://huggingface.co/{repo_id}")


if __name__ == "__main__":
    main()
