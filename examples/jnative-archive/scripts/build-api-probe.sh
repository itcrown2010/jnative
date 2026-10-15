#!/usr/bin/env bash
# Author: 张晓达 — API probe against packaged java.util.zip + ZipExtensions
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
OUT="${OUT_DIR:-$ROOT/build}"

# shellcheck source=jnative-sources.sh
source "$ROOT/scripts/jnative-sources.sh"

"$ROOT/scripts/build.sh"
LIB_SRCS=()
while IFS= read -r _src; do
  LIB_SRCS+=("$_src")
done < <(archive_lib_sources)
PROBE="$ROOT/src/ArchiveApiProbe.java"
BIN="$OUT/archive-api-probe"
OS="$(uname -s 2>/dev/null || echo unknown)"
JN_LINK=(-L "$OUT" -l jn_zip -l z)
if [[ "$OS" == Darwin || "$OS" == Linux ]]; then
  JN_LINK+=(-l iconv)
fi
if [[ "$OS" == Linux ]]; then
  JN_LINK+=(-l pthread)
fi
echo "==> jnative api-probe multi-unit → $BIN"
java -jar "$JAR" "${LIB_SRCS[@]}" "$PROBE" -o "$BIN" -O1 "${JN_LINK[@]}"
echo "OK $BIN"
(cd "$REPO" && "$BIN")
