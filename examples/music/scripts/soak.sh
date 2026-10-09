#!/usr/bin/env bash
# Author: 张晓达
# 30-minute soak harness against a running Music server.
set -euo pipefail
BASE="${1:-http://127.0.0.1:18500}"
MINUTES="${SOAK_MINUTES:-30}"
END=$(( $(date +%s) + MINUTES * 60 ))
OK=0
FAIL=0
while [ "$(date +%s)" -lt "$END" ]; do
  if curl -fsS "$BASE/health" >/dev/null && curl -fsS -H 'Range: bytes=0-1023' "$BASE/media/tracks/1" >/dev/null; then
    OK=$((OK + 1))
  else
    FAIL=$((FAIL + 1))
  fi
  sleep 2
done
echo "soak done ok=$OK fail=$FAIL minutes=$MINUTES"
[ "$FAIL" -eq 0 ]
