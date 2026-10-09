#!/usr/bin/env bash
# Author: 张晓达
# Fresh IR→WASM from current SudokuCore.java (never packages stale dist on failure).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
DIST="$ROOT/web/dist"
STAGE="$ROOT/.build-staging/web"
SRC="$ROOT/core/SudokuCore.java"

command -v wat2wasm >/dev/null || true
# Prefer native WABT (npm wabt Node shim OOMs on large WAT)
export PATH="/opt/homebrew/opt/wabt/bin:/usr/local/opt/wabt/bin:$PATH"

rm -rf "$STAGE"
mkdir -p "$STAGE"
echo "==> compiling $SRC → wasm32-browser"
java -jar "$JAR" build --target wasm32-browser --keep-temps "$SRC" -o "$STAGE"
for f in app.wasm app.js; do
  [[ -f "$STAGE/$f" ]] || { echo "missing $f"; exit 1; }
done
MAGIC="$(head -c 4 "$STAGE/app.wasm" | xxd -p)"
[[ "$MAGIC" == "0061736d" ]] || { echo "bad wasm magic $MAGIC"; exit 1; }

cp "$ROOT/web/index.html" "$STAGE/index.html"
cp "$ROOT/web/app-shell.js" "$STAGE/app-shell.js"
cp "$ROOT/web/bridge.js" "$STAGE/bridge.js"
cp "$ROOT/web/sudoku.css" "$STAGE/sudoku.css"

rm -rf "$DIST"
mkdir -p "$DIST"
cp -R "$STAGE"/. "$DIST"/
echo "==> web dist ready: $DIST"
ls -la "$DIST"
