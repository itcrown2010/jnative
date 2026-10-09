#!/usr/bin/env bash
# Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
OUT_DIR="${OUT_DIR:-$REPO/build/activity-monitor}"
OUT="$OUT_DIR/AmCore"

"$ROOT/scripts/build_native.sh"
mkdir -p "$OUT_DIR"
echo "==> headless AmCore → $OUT"
java -jar "$JAR" "$ROOT/core/AmCore.java" -o "$OUT" -O0 \
  -L "$OUT_DIR" -l jn_am
echo "Run: $OUT --dump | --bench 5"
