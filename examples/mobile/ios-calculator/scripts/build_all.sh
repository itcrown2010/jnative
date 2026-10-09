#!/usr/bin/env bash
# Author: 张晓达
# Entry point:
#   CalcEngine.java → WASM/Web → sync iOS → simulator .app → verify WASM
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "======== 1/4 compile + verify WASM/Web ========"
"$ROOT/scripts/build_web.sh"
node "$ROOT/scripts/verify_wasm.mjs"

echo "======== 2/4 sync iOS assets ========"
"$ROOT/scripts/sync_ios_assets.sh"

echo "======== 3/4 Xcode simulator build ========"
if command -v xcodebuild >/dev/null; then
  "$ROOT/scripts/build_ios.sh" || {
    ec=$?
    echo "NOTE: iOS build exited $ec (see BLOCKED/PARTIAL above)." >&2
    exit "$ec"
  }
else
  echo "BLOCKED: xcodebuild missing — WASM verified; iOS app not built." >&2
  exit 2
fi

echo "======== 4/4 done ========"
echo "App: $ROOT/build/IosCalculator.app"
echo "Screenshots: run scripts/run_simulator.sh (when runtime available)"
