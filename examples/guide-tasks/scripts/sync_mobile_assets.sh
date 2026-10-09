#!/usr/bin/env bash
# Author: 张晓达
# Build shared WASM then overlay mobile touch shell into staging (+ optional container www).
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
  cp "$MOBILE_WEB/index.html" "$MOBILE_WEB/app-shell.js" "$MOBILE_WEB/tasks-touch.css" "$dest/"
done

# If full container trees exist, replace their www (drop stale sudoku assets).
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
