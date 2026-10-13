#!/usr/bin/env bash
# Author: 张晓达
# Build shared WASM + web shell, overlay touch CSS for mobile containers.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/scripts/build_web.sh"

WEB_DIST="$ROOT/web/dist"
MOBILE_WEB="$ROOT/mobile/web"
AND_ST="$ROOT/mobile/staging/android-www"
IOS_ST="$ROOT/mobile/staging/ios-www"

rm -rf "$AND_ST" "$IOS_ST"
mkdir -p "$AND_ST" "$IOS_ST"

for dest in "$AND_ST" "$IOS_ST"; do
  cp "$WEB_DIST/app.wasm" "$WEB_DIST/app.js" "$dest/"
  cp "$WEB_DIST/index.html" "$WEB_DIST/app-shell.js" "$WEB_DIST/tasks.css" "$dest/"
  mkdir -p "$dest/lib" "$dest/api"
  cp "$WEB_DIST/lib/"*.js "$dest/lib/"
  cp "$WEB_DIST/api/hint.json" "$dest/api/hint.json"
  # Touch overlay + bridge (bridge client also lives in web/lib for app imports)
  cp "$MOBILE_WEB/tasks-touch.css" "$dest/"
  if [[ -f "$WEB_DIST/lib/bridge.js" ]]; then
    cp "$WEB_DIST/lib/bridge.js" "$dest/bridge.js"
    cp "$WEB_DIST/lib/bridge.js" "$MOBILE_WEB/bridge.js"
  else
    cp "$MOBILE_WEB/bridge.js" "$dest/"
  fi
  # Inject touch class + touch stylesheet into index
  python3 - "$dest/index.html" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
html = p.read_text()
if 'tasks-touch.css' not in html:
    html = html.replace(
        'href="./tasks.css"/>',
        'href="./tasks.css"/>\n  <link rel="stylesheet" href="./tasks-touch.css"/>',
    )
html = html.replace('<body>', '<body class="touch">', 1)
# Prefer bridge storage boot hint
if 'bridge.js' not in html:
    html = html.replace(
        '<script type="module" src="./app-shell.js"></script>',
        '<script type="module" src="./app-shell.js"></script>',
    )
p.write_text(html)
PY
done

AND_WWW="$ROOT/mobile/android/app/src/main/assets/www"
IOS_WWW="$ROOT/mobile/ios/Calculator/www"
if [[ -d "$ROOT/mobile/android" ]]; then
  rm -rf "$AND_WWW"
  mkdir -p "$AND_WWW"
  cp -R "$AND_ST"/. "$AND_WWW"/
  echo "==> synced android www → $AND_WWW"
fi
if [[ -d "$ROOT/mobile/ios/Calculator" ]]; then
  rm -rf "$IOS_WWW"
  mkdir -p "$IOS_WWW"
  cp -R "$IOS_ST"/. "$IOS_WWW"/
  echo "==> synced ios www → $IOS_WWW"
fi

echo "==> staging android-www → $AND_ST"
echo "==> staging ios-www → $IOS_ST"
ls -la "$AND_ST"
