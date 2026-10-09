#!/usr/bin/env bash
# Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
DIST="$ROOT/dist"
OPT_LEVEL="${1:-}"

if [[ ! -f "$JAR" ]]; then
  echo "error: missing $JAR — run mvn -DskipTests package" >&2
  exit 1
fi

rm -rf "$DIST"
mkdir -p "$DIST"
ARGS=(build --target wasm32-browser --keep-temps "$ROOT/ExcStress.java" -o "$DIST")
if [[ -n "$OPT_LEVEL" ]]; then
  ARGS+=("-O${OPT_LEVEL#O}")
fi
java -jar "$JAR" "${ARGS[@]}"
cp "$ROOT/index.html" "$DIST/index.html"
echo "exc-stress dist: $DIST (opt=${OPT_LEVEL:-default})"
ls -la "$DIST"
