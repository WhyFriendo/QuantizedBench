#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <model_id> [extra runner args...]"
  echo "Example: $0 phi3_mini"
  exit 1
fi

MODEL="$1"; shift
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

python -m bench.runner --config "$SCRIPT_DIR/bench/config.yaml" --backend mlc --model "$MODEL" "$@"
