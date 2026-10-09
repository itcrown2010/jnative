#!/usr/bin/env bash
# Author: 张晓达
# Phase-2: desktop + web guide-tasks + docs build
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

bash "$ROOT/scripts/docs/verify-phase1.sh"

echo "== guide-tasks core =="
bash "$ROOT/examples/guide-tasks/scripts/verify_core.sh"

echo "== guide-tasks desktop build =="
bash "$ROOT/examples/guide-tasks/scripts/build_desktop.sh"
[[ -x "$ROOT/build/guide-tasks/TasksDesktop" ]]

echo "== guide-tasks web =="
bash "$ROOT/examples/guide-tasks/scripts/verify_web.sh"

echo "PHASE2_VERIFY_OK"
