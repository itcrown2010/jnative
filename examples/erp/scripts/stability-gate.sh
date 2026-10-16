#!/usr/bin/env bash
# Author: 张晓达
# Unified ERP stability acceptance entry (isolated SQLite; never uses port 18700).
set -euo pipefail
export PATH=/usr/bin:/bin:/usr/sbin:/sbin:/opt/homebrew/bin
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
exec /usr/bin/python3 "$ROOT/examples/erp/scripts/stability-gate.py" "$@"
