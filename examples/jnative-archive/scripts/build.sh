#!/usr/bin/env bash
# Author: 张晓达
# Build jn_zip + JNative Archive CLI (multi-unit, no source concat).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
OUT="${OUT_DIR:-$ROOT/build}"
NATIVE="$ROOT/native"
TP="$ROOT/third_party"

# shellcheck source=jnative-sources.sh
source "$ROOT/scripts/jnative-sources.sh"

OS="$(uname -s 2>/dev/null || echo unknown)"
if [[ "$OS" == Darwin ]]; then
  MZ_BUILD="$TP/build-macos"
elif [[ "$OS" == Linux ]]; then
  MZ_BUILD="$TP/build-linux"
else
  echo "Use scripts/build.ps1 on Windows. This host OS=$OS" >&2
  exit 1
fi

if [[ ! -f "$JAR" ]]; then
  echo "Missing $JAR — build the JNative compiler first (mvn -q package)." >&2
  exit 1
fi

if [[ ! -f "$MZ_BUILD/libminizip-ng.a" ]]; then
  echo "==> fetch/build minizip-ng"
  "$ROOT/scripts/fetch-deps.sh"
fi

mkdir -p "$OUT"
echo "==> compile jn_zip.c"
clang -c -O2 -fPIC \
  -I"$NATIVE" -I"$TP/minizip-ng" \
  -DHAVE_ZLIB -DMZ_ZIP_NO_MAIN \
  -Wno-deprecated-declarations \
  "$NATIVE/jn_zip.c" -o "$OUT/jn_zip.o"

echo "==> fat libjn_zip.a (jn_zip + minizip-ng objects)"
TMP_OBJS="$OUT/mz_objs"
rm -rf "$TMP_OBJS"
mkdir -p "$TMP_OBJS"
(cd "$TMP_OBJS" && ar x "$MZ_BUILD/libminizip-ng.a")
rm -f "$OUT/libjn_zip.a"
# shellcheck disable=SC2086
ar rcs "$OUT/libjn_zip.a" "$OUT/jn_zip.o" $TMP_OBJS/*.o
ranlib "$OUT/libjn_zip.a" 2>/dev/null || true

echo "==> spike (C ABI smoke)"
EXTRA_LIBS=(-lz)
if [[ "$OS" == Darwin ]]; then
  EXTRA_LIBS+=(-liconv -framework Security -framework CoreFoundation)
elif [[ "$OS" == Linux ]]; then
  EXTRA_LIBS+=(-liconv -lpthread)
fi

clang -O2 \
  -I"$NATIVE" -I"$TP/minizip-ng" \
  -Wno-deprecated-declarations \
  "$NATIVE/jn_zip_spike.c" \
  "$OUT/libjn_zip.a" \
  "${EXTRA_LIBS[@]}" \
  -o "$OUT/jn_zip_spike"
(cd "$REPO" && "$OUT/jn_zip_spike")

echo "==> jnative multi-unit Archive CLI (package/import)"
# Portable array load (macOS bash 3.x has no mapfile)
LIB_SRCS=()
while IFS= read -r _src; do
  LIB_SRCS+=("$_src")
done < <(archive_lib_sources)
CLI_SRC="$ROOT/src/org/jnative/archive/JnArchiveCli.java"
BIN="$OUT/jn-archive"
JN_LINK=(-L "$OUT" -l jn_zip -l z)
if [[ "$OS" == Darwin || "$OS" == Linux ]]; then
  JN_LINK+=(-l iconv)
fi
if [[ "$OS" == Linux ]]; then
  JN_LINK+=(-l pthread)
fi
java -jar "$JAR" "${LIB_SRCS[@]}" "$CLI_SRC" -o "$BIN" -O1 "${JN_LINK[@]}"
echo "OK $BIN"
echo "Try: $BIN version"
echo "     printf 'secret' | $BIN --password-fd 0 --encrypt create /tmp/a.zip file.txt"
