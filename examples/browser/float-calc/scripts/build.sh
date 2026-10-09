#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
DIST="$ROOT/dist"
SRC="$ROOT/FloatCalc.java"

if [[ ! -f "$JAR" ]]; then
  echo "error: missing $JAR — run mvn -DskipTests package" >&2
  exit 1
fi

rm -rf "$DIST"
mkdir -p "$DIST"
java -jar "$JAR" build --target wasm32-browser --keep-temps "$SRC" -o "$DIST"
cp "$ROOT/index.html" "$DIST/index.html"
echo "float-calc dist: $DIST"
ls -la "$DIST"
