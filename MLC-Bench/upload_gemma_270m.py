"""Upload the locally converted gemma-3-270m-it MLC quant variants to the
user's Hugging Face account as public repos, one per variant, named after
the local output folders (e.g. gemma-3-270m-it-q4f16_1-MLC).

Usage:
    set HF_TOKEN=hf_...   # needs WRITE access this time, not just read
    python upload_gemma_270m.py
"""

from huggingface_hub import HfApi
from huggingface_hub.errors import LocalTokenNotFoundError

from export_gemma_270m import OUTPUT_BASE_DIR, QUANTIZATIONS

PRIVATE = False  # user chose public repos


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

    for quant in QUANTIZATIONS:
        folder = OUTPUT_BASE_DIR / f"gemma-3-270m-it-{quant}-MLC"
        if not (folder / "mlc-chat-config.json").is_file():
            print(f"Skipping {quant}: {folder} isn't fully converted yet (run export_gemma_270m.py first).")
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
