#!/usr/bin/env bash
# Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
DIST="$ROOT/dist"

if [[ ! -f "$JAR" ]]; then
  echo "error: missing $JAR — run mvn -DskipTests package" >&2
  exit 1
fi

rm -rf "$DIST"
mkdir -p "$DIST"
java -jar "$JAR" build --target wasm32-browser --keep-temps "$ROOT/GcStress.java" -o "$DIST"
echo "gc-stress dist: $DIST"
ls -la "$DIST"
