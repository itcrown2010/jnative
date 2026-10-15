#!/usr/bin/env bash
# Author: 张晓达 — build JNative Archive desktop GUI (multi-unit)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
OUT="${OUT_DIR:-$ROOT/build}"

# shellcheck source=jnative-sources.sh
source "$ROOT/scripts/jnative-sources.sh"

if [[ ! -f "$JAR" ]]; then
  echo "Missing $JAR — run: (cd repo && mvn -q package)" >&2
  exit 1
fi

# Ensure native zip engine is built
"$ROOT/scripts/build.sh"

LIB_SRCS=()
while IFS= read -r _src; do
  LIB_SRCS+=("$_src")
done < <(archive_lib_sources)
GUI_SRCS=()
while IFS= read -r _src; do
  GUI_SRCS+=("$_src")
done < <(find "$ROOT/src/gui" -name '*.java' | sort)
BIN="$OUT/jn-archive-gui"
echo "==> jnative GUI multi-unit → $BIN (${#GUI_SRCS[@]} gui units)"
OS="$(uname -s 2>/dev/null || echo unknown)"
JN_LINK=(-L "$OUT" -l jn_zip -l z)
if [[ "$OS" == Darwin || "$OS" == Linux ]]; then
  JN_LINK+=(-l iconv)
fi
if [[ "$OS" == Linux ]]; then
  JN_LINK+=(-l pthread)
fi
java -jar "$JAR" "${LIB_SRCS[@]}" "${GUI_SRCS[@]}" -o "$BIN" --app-type gui -O1 "${JN_LINK[@]}"
echo "OK $BIN"

if [[ "$OS" == Darwin ]]; then
  chmod +x "$ROOT/scripts/pack-macos-app.sh"
  "$ROOT/scripts/pack-macos-app.sh"
  echo "App: $OUT/JNativeArchive.app"
fi

echo "Run: $BIN"
echo "     open $OUT/JNativeArchive.app   # if packed"
