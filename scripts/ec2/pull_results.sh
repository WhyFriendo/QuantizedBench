#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
EC2_ENV_FILE="${EC2_ENV_FILE:-$REPO_ROOT/.ec2-instance.env}"
if [[ -f "$EC2_ENV_FILE" ]]; then
  # The file contains only connection metadata generated at instance launch.
  # shellcheck disable=SC1090
  source "$EC2_ENV_FILE"
fi

: "${EC2_HOST:?Set EC2_HOST to the instance public DNS name or IPv4 address}"
EC2_USER="${EC2_USER:-ec2-user}"
EC2_KEY="${EC2_KEY:-$HOME/.ssh/id_ed25519}"
REMOTE_RESULTS="${REMOTE_RESULTS:-/opt/quantizedbench/results/}"
LOCAL_RESULTS="${LOCAL_RESULTS:-$REPO_ROOT/results_ec2_repeats/}"
LOCK_FILE="${LOCK_FILE:-/tmp/quantizedbench-ec2-pull.lock}"

mkdir -p "$LOCAL_RESULTS"
exec 9>"$LOCK_FILE"
flock -n 9 || exit 0

rsync -az --partial --human-readable \
  -e "ssh -i $EC2_KEY -o BatchMode=yes -o StrictHostKeyChecking=accept-new" \
  "${EC2_USER}@${EC2_HOST}:${REMOTE_RESULTS}" \
  "$LOCAL_RESULTS"
