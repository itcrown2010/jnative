#!/usr/bin/env bash
# Author: 张晓达 — minimal java.util.zip import demo (native)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
OUT="${OUT_DIR:-$ROOT/build}"

if [[ ! -f "$JAR" ]]; then
  echo "Missing $JAR — mvn -q package first" >&2
  exit 1
fi

# Need libjn_zip
if [[ ! -f "$OUT/libjn_zip.a" ]]; then
  "$ROOT/scripts/build.sh"
fi

ZIP_SRCS=()
while IFS= read -r _src; do
  ZIP_SRCS+=("$_src")
done < <(find "$ROOT/src/java/util/zip" "$ROOT/src/org/jnative/archive/internal" -name '*.java' | sort)
DEMO="$ROOT/examples/ZipStyleDemo.java"
BIN="$OUT/zip-style-demo"
OS="$(uname -s 2>/dev/null || echo unknown)"
JN_LINK=(-L "$OUT" -l jn_zip -l z)
if [[ "$OS" == Darwin || "$OS" == Linux ]]; then
  JN_LINK+=(-l iconv)
fi
if [[ "$OS" == Linux ]]; then
  JN_LINK+=(-l pthread)
fi
mkdir -p "$OUT"
echo "==> jnative ZipStyleDemo → $BIN"
java -jar "$JAR" "${ZIP_SRCS[@]}" "$DEMO" -o "$BIN" -O1 "${JN_LINK[@]}"
(cd "$REPO" && "$BIN")
echo "OK demo"
