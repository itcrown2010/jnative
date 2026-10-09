#!/usr/bin/env bash
# Author: 张晓达
# Cross-file LLDB verification: breakpoint in MathUtil.java, locals in DebugMulti.java.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
DEMO="$ROOT/examples/debug/multifile"
JAR="$ROOT/target/jnative.jar"
BIN="$DEMO/build/DebugMulti"

if [[ ! -f "$JAR" ]]; then
  (cd "$ROOT" && mvn -q -DskipTests package)
fi
if ! command -v lldb >/dev/null 2>&1; then
  echo "SKIP: lldb not found"
  exit 2
fi

echo "== debug project build =="
java -jar "$JAR" build --project "$DEMO" --debug --format json
[[ -x "$BIN" || -f "$BIN" ]] || { echo "FAIL: missing artifact $BIN"; exit 1; }

echo "== run (expect 30 / 30) =="
out="$("$BIN")"
echo "$out"
echo "$out" | grep -q '^30$' || { echo "FAIL: stdout"; exit 1; }

ADD_LINE="$(grep -n 'int result = a + b' "$DEMO/src/MathUtil.java" | head -1 | cut -d: -f1)"
MAIN_LINE="$(grep -n 'int sum = MathUtil.add' "$DEMO/src/DebugMulti.java" | head -1 | cut -d: -f1)"
[[ -n "$ADD_LINE" && -n "$MAIN_LINE" ]] || { echo "FAIL: locate lines"; exit 1; }

LOG="$(mktemp)"
set +e
lldb -b \
  -o "settings set target.process.stop-on-sharedlibrary-events false" \
  -o "breakpoint set --file DebugMulti.java --line $MAIN_LINE" \
  -o "breakpoint set --file MathUtil.java --line $ADD_LINE" \
  -o "run" \
  -o "frame variable x y ready" \
  -o "continue" \
  -o "frame variable a b" \
  -o "thread step-over" \
  -o "frame variable result" \
  -o "bt" \
  -o "continue" \
  -- "$BIN" >"$LOG" 2>&1
rc=$?
set -e
cat "$LOG"

if grep -qiE 'Operation not permitted|cannot attach|developer mode' "$LOG"; then
  echo "SKIP: debugger attach blocked"
  exit 2
fi
[[ $rc -eq 0 ]] || { echo "FAIL: lldb $rc"; exit 1; }

grep -E "DebugMulti\.java:$MAIN_LINE" "$LOG" >/dev/null \
  || { echo "FAIL: did not stop in DebugMulti.java"; exit 1; }
grep -E "MathUtil\.java:$ADD_LINE" "$LOG" >/dev/null \
  || { echo "FAIL: did not stop in MathUtil.java"; exit 1; }
grep -E '\(int\) x = 10' "$LOG" >/dev/null || { echo "FAIL: x"; exit 1; }
grep -E '\(int\) y = 20' "$LOG" >/dev/null || { echo "FAIL: y"; exit 1; }
grep -E '\(int\) a = 10' "$LOG" >/dev/null || { echo "FAIL: a"; exit 1; }
grep -E '\(int\) b = 20' "$LOG" >/dev/null || { echo "FAIL: b"; exit 1; }
grep -E '\(int\) result = 30' "$LOG" >/dev/null || { echo "FAIL: result"; exit 1; }
grep -E 'MathUtil|DebugMulti|jn_main|add' "$LOG" >/dev/null \
  || { echo "FAIL: backtrace"; exit 1; }

# Ensure DI does not point only at concatenated unit.java
if grep -E 'stop reason = breakpoint' -A2 "$LOG" | grep -E 'unit\.java'; then
  echo "FAIL: stop attributed to concatenated unit.java"
  exit 1
fi

echo "PASS: multifile LLDB source mapping verified"
exit 0
