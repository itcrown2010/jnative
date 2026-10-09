#!/usr/bin/env bash
# Author: 张晓达
# Verify references remain valid across System.gc() under LLDB (macOS arm64).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DEMO_DIR="$ROOT/examples/debug"
SRC="$DEMO_DIR/GcDebugDemo.java"
BIN="$DEMO_DIR/GcDebugDemo"
JAR="$ROOT/target/jnative.jar"
FMT="$ROOT/tools/lldb/jnative_formatters.py"

if [[ ! -f "$JAR" ]]; then
  (cd "$ROOT" && mvn -q -DskipTests package)
fi
command -v lldb >/dev/null || { echo "SKIP: lldb missing"; exit 2; }

echo "== debug compile GcDebugDemo =="
java -jar "$JAR" --debug "$SRC" -o "$BIN"

LINE_BEFORE="$(grep -n 'System\.gc()' "$SRC" | grep -v '^\s*\*' | head -1 | cut -d: -f1)"
LINE_AFTER="$(grep -n 'markerAfter = afterLen' "$SRC" | head -1 | cut -d: -f1)"
[[ -n "$LINE_BEFORE" && -n "$LINE_AFTER" ]]
echo "== breakpoints GcDebugDemo.java:$LINE_BEFORE (before GC) and :$LINE_AFTER (after) =="

LOG="$(mktemp)"
ADDR_LOG="$(mktemp)"
set +e
lldb -b \
  -o "settings set target.process.stop-on-sharedlibrary-events false" \
  -o "command script import $FMT" \
  -o "breakpoint set --file GcDebugDemo.java --line $LINE_BEFORE" \
  -o "breakpoint set --file GcDebugDemo.java --line $LINE_AFTER" \
  -o "run" \
  -o "frame variable box beforeLen before0" \
  -o "p/x box" \
  -o "continue" \
  -o "frame variable box beforeLen before0 afterLen after0" \
  -o "p/x box" \
  -o "continue" \
  -- "$BIN" >"$LOG" 2>&1
rc=$?
set -e
cat "$LOG"

if grep -qiE 'Operation not permitted|cannot attach|developer mode' "$LOG"; then
  echo "SKIP: debugger attach blocked"; exit 2
fi
[[ $rc -eq 0 ]] || { echo "FAIL: lldb $rc"; exit 1; }

grep -E "GcDebugDemo\.java:$LINE_BEFORE" "$LOG" >/dev/null \
  || { echo "FAIL: before-GC breakpoint missed"; exit 1; }
grep -E "GcDebugDemo\.java:$LINE_AFTER" "$LOG" >/dev/null \
  || { echo "FAIL: after-GC breakpoint missed"; exit 1; }

hits=$(grep -c 'GC前中文' "$LOG" || true)
[[ "$hits" -ge 2 ]] || { echo "FAIL: label not readable across GC (hits=$hits)"; exit 1; }
grep -E 'nums=array\.i32\[3\] \{7, 8, 9\}' "$LOG" >/dev/null \
  || { echo "FAIL: array values wrong across GC"; exit 1; }

# Capture two p/x box addresses — may differ if young GC moved the object
addrs=$(grep -E '^\(GcBox \*\) 0x' "$LOG" | sed -E 's/.*(0x[0-9a-fA-F]+).*/\1/' | head -2)
echo "box addresses: $addrs"
a1=$(echo "$addrs" | awk 'NR==1')
a2=$(echo "$addrs" | awk 'NR==2')
[[ -n "$a1" && -n "$a2" ]] || { echo "FAIL: missing box addresses"; exit 1; }
if [[ "$a1" != "$a2" ]]; then
  echo "OK: object relocated by GC ($a1 -> $a2) and still readable"
else
  echo "OK: object address stable ($a1); values still correct (relocation not required)"
fi

grep -E '\(int\) afterLen = 11' "$LOG" >/dev/null \
  || { echo "FAIL: afterLen not 11 after GC"; exit 1; }
grep -E '\(int\) after0 = 7' "$LOG" >/dev/null \
  || { echo "FAIL: after0 not 7 after GC"; exit 1; }

grep -E 'Process .* exited with status = 0' "$LOG" >/dev/null \
  || { echo "FAIL: process did not exit 0"; exit 1; }

echo "PASS: GC reference validity under LLDB verified"
echo "NOTE: does not claim full heap consistency if paused mid-collection"
echo "NOTE: locals not yet assigned at a stop are undefined (not forged)"
exit 0
