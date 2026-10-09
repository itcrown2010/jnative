#!/usr/bin/env bash
# Build web (shared WASM core) then overlay mobile touch shell into android/ios www.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/scripts/build_web.sh"

WEB_DIST="$ROOT/web/dist"
MOBILE_WEB="$ROOT/mobile/web"
AND_WWW="$ROOT/mobile/android/app/src/main/assets/www"
IOS_WWW="$ROOT/mobile/ios/Sudoku/www"
# Calculator template used Calculator/www — detect
if [[ ! -d "$(dirname "$IOS_WWW")" ]]; then
  if [[ -d "$ROOT/mobile/ios/Calculator" ]]; then
    IOS_WWW="$ROOT/mobile/ios/Calculator/www"
  fi
fi

rm -rf "$AND_WWW" "$IOS_WWW"
mkdir -p "$AND_WWW" "$IOS_WWW"

# Shared wasm/js from fresh web build
cp "$WEB_DIST/app.wasm" "$WEB_DIST/app.js" "$AND_WWW/"
cp "$WEB_DIST/app.wasm" "$WEB_DIST/app.js" "$IOS_WWW/"
# Touch-oriented shell
cp "$MOBILE_WEB/index.html" "$MOBILE_WEB/app-shell.js" "$MOBILE_WEB/bridge.js" "$MOBILE_WEB/sudoku.css" "$AND_WWW/"
cp "$MOBILE_WEB/index.html" "$MOBILE_WEB/app-shell.js" "$MOBILE_WEB/bridge.js" "$MOBILE_WEB/sudoku.css" "$IOS_WWW/"

echo "==> synced android www → $AND_WWW"
echo "==> synced ios www → $IOS_WWW"
