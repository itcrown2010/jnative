#!/usr/bin/env bash
# Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
OUT_DIR="$REPO/build/ui-workbench"
OUT="$OUT_DIR/UiWorkbench"
mkdir -p "$OUT_DIR"
rm -rf "$OUT.app"
echo "==> $OUT"
java -jar "$JAR" "$ROOT/UiWorkbench.java" -o "$OUT" --app-type gui -O0
"$REPO/scripts/macos/pack-app.sh" \
  --bin "$OUT" \
  --name "UiWorkbench" \
  --id "io.jnative.examples.ui-workbench" \
  --out "$OUT_DIR" \
  --version "0.1.0"
echo "App: open $OUT.app"
