#!/usr/bin/env bash
# Author: 张晓达
# Copy verified dist/ into Android assets and iOS bundle resources.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$ROOT/dist"
ANDROID_WWW="$ROOT/android/app/src/main/assets/www"
IOS_WWW="$ROOT/ios/Calculator/www"

if [[ ! -f "$DIST/app.wasm" || ! -f "$DIST/app.js" || ! -f "$DIST/index.html" ]]; then
  echo "error: dist incomplete — run scripts/build_web.sh first" >&2
  exit 1
fi

sync_dir() {
  local dest="$1"
  rm -rf "$dest"
  mkdir -p "$dest"
  cp "$DIST/app.wasm" "$DIST/app.js" "$DIST/index.html" "$DIST/bridge.js" "$DIST/app-shell.js" "$dest/"
  echo "synced → $dest"
}

sync_dir "$ANDROID_WWW"
sync_dir "$IOS_WWW"
