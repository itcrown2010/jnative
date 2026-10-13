#!/usr/bin/env bash
# Publish local/ui-controls to the example filesystem registry. Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
REG="$ROOT/registry"
rm -rf "$REG/local"
mkdir -p "$REG"
java -jar "$JAR" publish \
  --project "$ROOT/library" \
  --registry "$REG"
echo "Registry: $REG"
