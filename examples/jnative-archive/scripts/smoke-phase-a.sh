#!/usr/bin/env bash
# Author: 张晓达
# Phase A evidence: create / list / test / extract + system unzip interop + reverse read.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BIN="${1:-$ROOT/build/jn-archive}"
WORK="$ROOT/build/phase-a-smoke"
TD="$ROOT/testdata"

if [[ ! -x "$BIN" ]]; then
  echo "missing $BIN — run scripts/build.sh first" >&2
  exit 1
fi

rm -rf "$WORK"
mkdir -p "$WORK/in" "$WORK/out" "$WORK/out_ext"

# Fixed fixture set (Phase A)
printf 'hello phase-a\n' > "$WORK/in/hello.txt"
printf 'unicode path ok\n' > "$WORK/in/中文名.txt"
: > "$WORK/in/empty.dat"
mkdir -p "$WORK/in/subdir"
printf 'nested\n' > "$WORK/in/subdir/nested.txt"
# copy curated testdata if present
if [[ -d "$TD" ]]; then
  cp -R "$TD"/. "$WORK/in/" 2>/dev/null || true
fi

echo "==> version"
"$BIN" version

echo "==> create"
"$BIN" create "$WORK/plain.zip" \
  "$WORK/in/hello.txt" \
  "$WORK/in/中文名.txt" \
  "$WORK/in/empty.dat"

echo "==> list"
"$BIN" list "$WORK/plain.zip"

echo "==> test"
"$BIN" test "$WORK/plain.zip"

echo "==> extract"
"$BIN" extract "$WORK/plain.zip" "$WORK/out"

echo "==> content check"
diff -u "$WORK/in/hello.txt" "$WORK/out/hello.txt"
diff -u "$WORK/in/中文名.txt" "$WORK/out/中文名.txt"
test -f "$WORK/out/empty.dat"
test ! -s "$WORK/out/empty.dat"

echo "==> interop: system unzip -t (external tool reads our archive)"
unzip -t "$WORK/plain.zip"

echo "==> reverse: system zip → our test/extract"
(cd "$WORK/in" && zip -q "$WORK/external.zip" hello.txt "中文名.txt" empty.dat)
"$BIN" test "$WORK/external.zip"
"$BIN" extract "$WORK/external.zip" "$WORK/out_ext"
diff -u "$WORK/in/hello.txt" "$WORK/out_ext/hello.txt"

# Streaming API path (ZipOutputStream putNextEntry) via tiny helper if present later
echo "PHASE_A_SMOKE_PASS"
echo "Artifacts under $WORK"
