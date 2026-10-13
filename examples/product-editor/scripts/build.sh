#!/usr/bin/env bash
# Build ProductEditor with local/ui-controls via .jnpkg registry. Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
REG="$REPO/examples/ui-controls-pkg/registry"
OUT_DIR="$REPO/build/product-editor"
mkdir -p "$OUT_DIR"
rm -rf "$OUT_DIR/ProductEditor.app"

# Ensure control package is published
"$REPO/examples/ui-controls-pkg/scripts/publish.sh"

rm -rf "$ROOT/.jnative" "$ROOT/jnative.lock"
java -jar "$JAR" build \
  --project "$ROOT" \
  --registry "$REG" \
  -O0

OUT="$OUT_DIR/ProductEditor"
if [[ ! -x "$OUT" ]]; then
  OUT="$(find "$ROOT" "$OUT_DIR" -type f -name ProductEditor 2>/dev/null | head -1)"
fi
if [[ -z "$OUT" || ! -x "$OUT" ]]; then
  echo "FAIL: ProductEditor binary not found"
  exit 1
fi
# Normalize to build/product-editor/ProductEditor
if [[ "$OUT" != "$OUT_DIR/ProductEditor" ]]; then
  cp -f "$OUT" "$OUT_DIR/ProductEditor"
  OUT="$OUT_DIR/ProductEditor"
  chmod +x "$OUT"
fi

"$REPO/scripts/macos/pack-app.sh" \
  --bin "$OUT" \
  --name "ProductEditor" \
  --id "io.jnative.examples.product-editor" \
  --out "$OUT_DIR" \
  --version "0.1.0"
echo "Self-test: $OUT --self-test"
echo "App: open $OUT.app"
