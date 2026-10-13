#!/usr/bin/env bash
# Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
OUT_DIR="$REPO/build/desktop-sample"
OUT="$OUT_DIR/DesktopSample"
mkdir -p "$OUT_DIR"
rm -rf "$OUT.app"
java -jar "$JAR" "$ROOT/src/Main.java" -o "$OUT" --app-type gui -O0
"$REPO/scripts/macos/pack-app.sh" \
  --bin "$OUT" \
  --name "DesktopSample" \
  --id "io.jnative.examples.desktop-sample" \
  --out "$OUT_DIR" \
  --version "0.1.0"
echo "Self-test: $OUT --self-test"
echo "App: open $OUT.app"
