#!/usr/bin/env bash
# Author: 张晓达
# JNative source → fresh WebUI/WASM dist (never packages a previous success on failure).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
DIST="$ROOT/dist"
STAGE="$ROOT/.build-staging/web"
SRC="$ROOT/src/CalculatorApp.java"

if [[ ! -f "$JAR" ]]; then
  echo "error: jnative jar not found at $JAR" >&2
  echo "Build the toolchain first: mvn -DskipTests package" >&2
  exit 1
fi
if [[ ! -f "$SRC" ]]; then
  echo "error: missing $SRC" >&2
  exit 1
fi

command -v wat2wasm >/dev/null || {
  echo "error: wat2wasm not found in PATH (install WABT)" >&2
  exit 1
}

rm -rf "$STAGE"
mkdir -p "$STAGE"

echo "==> compiling $SRC → wasm32-browser (staging)"
java -jar "$JAR" build --target wasm32-browser --keep-temps "$SRC" -o "$STAGE"

# Required artifacts from this compile only
for f in app.wasm app.js; do
  if [[ ! -f "$STAGE/$f" ]]; then
    echo "error: compile did not produce $STAGE/$f" >&2
    exit 1
  fi
done

# Magic: \0asm
MAGIC="$(head -c 4 "$STAGE/app.wasm" | xxd -p)"
if [[ "$MAGIC" != "0061736d" ]]; then
  echo "error: app.wasm missing WASM magic (got $MAGIC)" >&2
  exit 1
fi

# Overlay web shell (same relative paths for Android/iOS packaging)
cp "$ROOT/web/index.html" "$STAGE/index.html"
cp "$ROOT/web/bridge.js" "$STAGE/bridge.js"
cp "$ROOT/web/app-shell.js" "$STAGE/app-shell.js"

# Atomic replace of dist — only after successful compile + copy
rm -rf "$DIST"
mkdir -p "$DIST"
cp -R "$STAGE"/. "$DIST"/

echo "==> web dist ready: $DIST"
ls -la "$DIST"
