#!/usr/bin/env bash
# Publish package then build consumer (no hand-copied control sources). Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
"$ROOT/scripts/publish.sh"
rm -rf "$ROOT/consumer/.jnative" "$ROOT/consumer/jnative.lock" "$ROOT/consumer/build"
java -jar "$JAR" build \
  --project "$ROOT/consumer" \
  --registry "$ROOT/registry" \
  -O0
OUT="$ROOT/consumer/build/UiControlsDemo"
if [[ ! -x "$OUT" ]]; then
  OUT="$(find "$ROOT/consumer" -type f -name UiControlsDemo 2>/dev/null | head -1)"
fi
echo "Binary: $OUT"
"$OUT" --self-test
"$REPO/scripts/macos/pack-app.sh" \
  --bin "$OUT" \
  --name "UiControlsDemo" \
  --id "io.jnative.examples.ui-controls-demo" \
  --out "$ROOT/consumer/build" \
  --version "0.1.0" || true
echo "Run GUI: $OUT"
echo "App: open $ROOT/consumer/build/UiControlsDemo.app"
