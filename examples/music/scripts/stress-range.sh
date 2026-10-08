#!/usr/bin/env bash
# Concurrent Range streaming stress (10 / 50 / 100).
set -euo pipefail
BASE="${1:-http://127.0.0.1:18500}"
N="${2:-50}"
echo "stress N=$N against $BASE"
seq 1 "$N" | xargs -P "$N" -I{} curl -fsS -o /dev/null -H 'Range: bytes=0-4095' "$BASE/media/tracks/1"
echo "stress ok"
