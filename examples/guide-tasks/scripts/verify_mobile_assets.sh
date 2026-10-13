#!/usr/bin/env bash
# Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/scripts/sync_mobile_assets.sh"
ST="$ROOT/mobile/staging/android-www"
for f in app.wasm app.js index.html app-shell.js tasks.css tasks-touch.css bridge.js api/hint.json lib/router.js; do
  [[ -f "$ST/$f" ]] || { echo "missing $f"; exit 1; }
done
# Reuse web WASM functional check
"$ROOT/scripts/verify_web.sh" >/dev/null
rg -q 'class="touch"' "$ST/index.html"
rg -q 'safe-area-inset' "$ST/tasks-touch.css"
rg -q 'page-form' "$ST/index.html"
echo "GUIDE_TASKS_MOBILE_ASSETS_OK"
