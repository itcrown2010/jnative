#!/usr/bin/env bash
# Author: 张晓达
# Copy freshly built dist/ into the Xcode www bundle. Refuses stale sync.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$ROOT/dist"
WWW="$ROOT/ios/IosCalculator/www"

if [[ ! -f "$DIST/app.wasm" || ! -f "$DIST/index.html" || ! -f "$DIST/app-shell.js" || ! -f "$DIST/app.js" ]]; then
  echo "error: dist incomplete — run scripts/build_web.sh first" >&2
  exit 1
fi

# Ensure WASM is newer than source (or equal after fresh build)
SRC="$ROOT/src/CalcEngine.java"
if [[ "$SRC" -nt "$DIST/app.wasm" ]]; then
  echo "error: dist/app.wasm is older than CalcEngine.java — rebuild web first" >&2
  exit 1
fi

rm -rf "$WWW"
mkdir -p "$WWW"
cp "$DIST/app.wasm" "$DIST/app.js" "$DIST/index.html" "$DIST/app-shell.js" "$WWW/"
# Optional WAT for debugging (not required at runtime)
if [[ -f "$DIST/app.wat" ]]; then
  cp "$DIST/app.wat" "$WWW/"
fi

echo "==> synced assets → $WWW"
ls -la "$WWW"
