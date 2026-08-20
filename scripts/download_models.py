#!/usr/bin/env python3
"""Download GGUF weights listed in bench/models_manifest.json into gguf_models/.

Resumable: files that already exist and match their recorded sha256 are
skipped. Partially downloaded files are written to a .part path and only
renamed into place after the hash check passes, so a killed/interrupted run
never leaves a corrupt file at the final path.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import requests

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_MANIFEST = REPO_ROOT / "bench" / "models_manifest.json"
DEFAULT_OUTPUT_DIR = REPO_ROOT / "gguf_models"
CHUNK_SIZE = 8 * 1024 * 1024


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(CHUNK_SIZE), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_entries(manifest_path: Path, families: set[str] | None) -> list[dict]:
    data = json.loads(manifest_path.read_text())
    entries = []
    for model_id, info in data.items():
        if families and model_id not in families:
            continue
        for row in info["rows"]:
            entries.append({"model_id": model_id, **row})
    return entries


def download_one(entry: dict, output_dir: Path, retries: int, force: bool) -> tuple[str, str]:
    dest = output_dir / entry["filename"]
    part = dest.with_suffix(dest.suffix + ".part")

    if dest.exists() and not force:
        if sha256_of(dest) == entry["sha256"]:
            return entry["filename"], "already-ok"
        dest.unlink()

    last_error = None
    for attempt in range(1, retries + 1):
        try:
            with requests.get(entry["url"], stream=True, timeout=60) as resp:
                resp.raise_for_status()
                with part.open("wb") as fh:
                    for chunk in resp.iter_content(chunk_size=CHUNK_SIZE):
                        if chunk:
                            fh.write(chunk)
            actual = sha256_of(part)
            if actual != entry["sha256"]:
                part.unlink(missing_ok=True)
                raise ValueError(f"sha256 mismatch (expected {entry['sha256'][:12]}..., got {actual[:12]}...)")
            part.rename(dest)
            return entry["filename"], "downloaded"
        except Exception as exc:  # noqa: BLE001 - report and retry
            last_error = exc
            part.unlink(missing_ok=True)

    return entry["filename"], f"FAILED after {retries} attempts: {last_error}"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--concurrency", type=int, default=4, help="parallel downloads")
    parser.add_argument("--retries", type=int, default=3)
    parser.add_argument("--force", action="store_true", help="redownload even if a valid file already exists")
    parser.add_argument(
        "--family",
        action="append",
        help="only download this model_id (repeatable); default is all families in the manifest",
    )
    parser.add_argument("--list", action="store_true", help="print planned downloads and exit")
    args = parser.parse_args()

    families = set(args.family) if args.family else None
    entries = load_entries(args.manifest, families)
    if not entries:
        print("No entries matched.", file=sys.stderr)
        return 1

    total_gb = sum(e["memory_gb"] for e in entries)
    print(f"Planned: {len(entries)} files, ~{total_gb:.1f} GB, -> {args.output_dir}")
    if args.list:
        for e in entries:
            print(f"  [{e['model_id']}] {e['filename']} ({e['memory_gb']} GB)")
        return 0

    args.output_dir.mkdir(parents=True, exist_ok=True)

    ok, failed = 0, []
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        futures = {
            pool.submit(download_one, e, args.output_dir, args.retries, args.force): e
            for e in entries
        }
        for i, future in enumerate(as_completed(futures), 1):
            filename, status = future.result()
            print(f"[{i}/{len(entries)}] {filename}: {status}")
            if status.startswith("FAILED"):
                failed.append(filename)
            else:
                ok += 1

    print(f"\nDone: {ok} ok, {len(failed)} failed.")
    if failed:
        print("Failed files:")
        for f in failed:
            print(f"  - {f}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
