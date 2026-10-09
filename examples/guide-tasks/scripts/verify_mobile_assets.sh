#!/usr/bin/env bash
# Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/scripts/sync_mobile_assets.sh"
ST="$ROOT/mobile/staging/android-www"
for f in app.wasm app.js index.html app-shell.js tasks-touch.css; do
  [[ -f "$ST/$f" ]] || { echo "missing $f"; exit 1; }
done
# Reuse web WASM functional check
"$ROOT/scripts/verify_web.sh" >/dev/null
# Ensure touch shell mentions body.touch
rg -q 'class="touch"' "$ROOT/mobile/web/index.html"
rg -q 'safe-area-inset' "$ROOT/mobile/web/tasks-touch.css"
echo "GUIDE_TASKS_MOBILE_ASSETS_OK"
