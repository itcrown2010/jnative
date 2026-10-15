#!/usr/bin/env bash
# Author: 张晓达 — launch GUI briefly, capture screenshots, keep CLI regressions green.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BIN="${1:-$ROOT/build/jn-archive-gui}"
CLI="$ROOT/build/jn-archive"
SHOT="$ROOT/build/gui-shots"
WORK="$ROOT/build/gui-accept"

if [[ ! -x "$BIN" ]]; then
  echo "missing $BIN — run scripts/build-gui.sh" >&2
  exit 1
fi
if [[ ! -x "$CLI" ]]; then
  echo "missing $CLI" >&2
  exit 1
fi

rm -rf "$SHOT" "$WORK"
mkdir -p "$SHOT" "$WORK/in/tree/sub" "$WORK/in/empty_dir" "$WORK/out"
printf 'gui-a\n' > "$WORK/in/a.txt"
printf '中文\n' > "$WORK/in/中文 空格.txt"
: > "$WORK/in/empty.dat"
printf 'deep\n' > "$WORK/in/tree/sub/deep.txt"
mkdir -p "$WORK/in/tree/中文目录"
printf 'mid\n' > "$WORK/in/tree/中文目录/file.txt"
"$CLI" create "$WORK/demo.zip" "$WORK/in/a.txt" "$WORK/in/中文 空格.txt" "$WORK/in/empty.dat" \
  "$WORK/in/tree" "$WORK/in/empty_dir"

echo "==> launch GUI with demo zip (5s) + screenshot"
"$BIN" --open "$WORK/demo.zip" &
GPID=$!
sleep 3
# Capture front window if possible
screencapture -x "$SHOT/01-main-open.png" || true
# Also try window-id capture of process
sleep 2
screencapture -x "$SHOT/02-main-later.png" || true
kill "$GPID" 2>/dev/null || true
wait "$GPID" 2>/dev/null || true
sleep 1

echo "==> empty window screenshot"
"$BIN" &
GPID=$!
sleep 2
screencapture -x "$SHOT/03-empty.png" || true
kill "$GPID" 2>/dev/null || true
wait "$GPID" 2>/dev/null || true

echo "==> CLI regressions (security + accept-core)"
"$ROOT/scripts/regress-security.sh"
"$ROOT/scripts/accept-core.sh" "$CLI"

echo "GUI_ACCEPT_PASS"
echo "Screenshots: $SHOT"
ls -la "$SHOT"
echo "WINDOWS_GUI_UNVERIFIED"
echo "ZIP64_4GIB_UNVERIFIED"
