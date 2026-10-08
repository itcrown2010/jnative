#!/usr/bin/env bash
# Batch-verify source-level debugging for DebugDemo (macOS arm64 MVP).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DEMO_DIR="$ROOT/examples/debug"
SRC="$DEMO_DIR/DebugDemo.java"
BIN="$DEMO_DIR/DebugDemo"
JAR="$ROOT/target/jnative.jar"

if [[ ! -f "$JAR" ]]; then
  (cd "$ROOT" && mvn -q -DskipTests package)
fi

if ! command -v lldb >/dev/null 2>&1; then
  echo "SKIP: lldb not found on PATH"
  exit 2
fi

echo "== debug compile =="
java -jar "$JAR" --debug "$SRC" -o "$BIN"
echo "== run (expect 30) =="
out="$("$BIN")"
echo "$out"
[[ "$out" == "30" ]] || { echo "FAIL: unexpected stdout: $out"; exit 1; }

# Find line of: int sum = add(x, y);
LINE="$(grep -n 'int sum = add' "$SRC" | head -1 | cut -d: -f1)"
[[ -n "$LINE" ]] || { echo "FAIL: could not locate sum line"; exit 1; }
echo "== lldb breakpoint at DebugDemo.java:$LINE =="

LOG="$(mktemp)"
# SIP / developer mode may block attaching; capture errors clearly.
set +e
# Stepping notes:
# - On first stop at `sum = add(...)`, check x/y only (sum not yet assigned).
# - step-in may land on add's prologue before param stores finish; step-over to the
#   `result = a + b` line, then check a/b (initialized). Another step-over runs the
#   add so `result` becomes 30 before we read it.
lldb -b \
  -o "settings set target.process.stop-on-sharedlibrary-events false" \
  -o "breakpoint set --file DebugDemo.java --line $LINE" \
  -o "run" \
  -o "frame variable x y" \
  -o "thread step-in" \
  -o "thread step-over" \
  -o "frame variable a b" \
  -o "thread step-over" \
  -o "frame variable result" \
  -o "bt" \
  -o "continue" \
  -- "$BIN" >"$LOG" 2>&1
rc=$?
set -e

cat "$LOG"
echo "== checking log =="

if grep -qiE 'Operation not permitted|cannot attach|need to be run as root|developer mode' "$LOG"; then
  echo "SKIP: system blocked debugger attach (SIP / Developer Mode / permissions)"
  exit 2
fi

if [[ $rc -ne 0 ]]; then
  echo "FAIL: lldb exited $rc"
  exit 1
fi

grep -E "Breakpoint [0-9]+:|stop reason = breakpoint" "$LOG" >/dev/null \
  || { echo "FAIL: breakpoint did not hit"; exit 1; }

grep -E "DebugDemo\.java:$LINE" "$LOG" >/dev/null \
  || grep -E "DebugDemo\.java:10" "$LOG" >/dev/null \
  || { echo "FAIL: stop location not in original DebugDemo.java"; exit 1; }

# Variables: only assert after initialization (see stepping notes above).
grep -E '\(int\) x = 10' "$LOG" >/dev/null || { echo "FAIL: x != 10"; exit 1; }
grep -E '\(int\) y = 20' "$LOG" >/dev/null || { echo "FAIL: y != 20"; exit 1; }
grep -E '\(int\) a = 10' "$LOG" >/dev/null || { echo "FAIL: a != 10 after params initialized"; exit 1; }
grep -E '\(int\) b = 20' "$LOG" >/dev/null || { echo "FAIL: b != 20 after params initialized"; exit 1; }
grep -E '\(int\) result = 30' "$LOG" >/dev/null || { echo "FAIL: result != 30 after add"; exit 1; }

# Stack should mention add and main (mangled or DI names)
grep -E '_JN_DebugDemo_add|jn_main' "$LOG" >/dev/null \
  || { echo "FAIL: backtrace missing user frames"; exit 1; }

echo "PASS: LLDB source debug verification succeeded"
exit 0
