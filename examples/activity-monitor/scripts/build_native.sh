#!/usr/bin/env bash
# Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
OUT_DIR="${OUT_DIR:-$REPO/build/activity-monitor}"
mkdir -p "$OUT_DIR"

SRC="$ROOT/native/jn_am_macos.c"
OBJ="$OUT_DIR/jn_am_macos.o"
LIB="$OUT_DIR/libjn_am.a"

echo "==> compile $SRC"
clang -c -O2 -fPIC -I"$ROOT/native" "$SRC" -o "$OBJ"
rm -f "$LIB"
ar rcs "$LIB" "$OBJ"
echo "OK $LIB"

# test workloads
clang -O1 "$ROOT/testdata/am_idle.c" -o "$OUT_DIR/am_idle"
clang -O1 "$ROOT/testdata/am_burn.c" -o "$OUT_DIR/am_burn"
clang -O1 "$ROOT/testdata/am_ignore_term.c" -o "$OUT_DIR/am_ignore_term"
echo "OK test binaries in $OUT_DIR"
