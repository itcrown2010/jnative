#!/usr/bin/env bash
# Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
DIST="$ROOT/dist"
MERGED="$DIST/merged-order-list.java"

if [[ ! -f "$JAR" ]]; then
  echo "error: missing $JAR — run mvn -DskipTests package" >&2
  exit 1
fi

rm -rf "$DIST"
mkdir -p "$DIST"
# CLI is single-file; merge units (true multi-unit covered by WasmBrowserCoreTest).
cat "$ROOT/OrderItem.java" "$ROOT/OrderListApp.java" > "$MERGED"
java -jar "$JAR" build --target wasm32-browser --keep-temps "$MERGED" -o "$DIST"
cp "$ROOT/index.html" "$DIST/index.html"
echo "order-list dist: $DIST"
ls -la "$DIST"
