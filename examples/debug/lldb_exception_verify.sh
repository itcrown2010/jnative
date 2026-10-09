#!/usr/bin/env bash
# Author: 张晓达
# Verify unhandled-exception stop + throw-site user stack (macOS arm64).
#
# JNative uses TLS pending-exception propagation (not DWARF unwind). At
# jnative_debugger_on_uncaught the Java frames have already returned; the live
# user stack is available at jnative_exception_throw (and via jn_ex_print_stack).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DEMO_DIR="$ROOT/examples/debug"
SRC="$DEMO_DIR/ExceptionDebugDemo.java"
BIN="$DEMO_DIR/ExceptionDebugDemo"
JAR="$ROOT/target/jnative.jar"
FMT="$ROOT/tools/lldb/jnative_formatters.py"

if [[ ! -f "$JAR" ]]; then
  (cd "$ROOT" && mvn -q -DskipTests package)
fi
command -v lldb >/dev/null || { echo "SKIP: lldb missing"; exit 2; }

echo "== debug compile ExceptionDebugDemo =="
java -jar "$JAR" --debug "$SRC" -o "$BIN"

LOG="$(mktemp)"
set +e
lldb -b \
  -o "settings set target.process.stop-on-sharedlibrary-events false" \
  -o "command script import $FMT" \
  -o "breakpoint set --name jnative_exception_throw" \
  -o "breakpoint set --name jnative_debugger_on_uncaught" \
  -o "run" \
  -o "bt" \
  -o "continue" \
  -o "register read x0" \
  -o "continue" \
  -- "$BIN" >"$LOG" 2>&1
rc=$?
set -e
cat "$LOG"

if grep -qiE 'Operation not permitted|cannot attach|developer mode' "$LOG"; then
  echo "SKIP: debugger attach blocked"; exit 2
fi

# Throw site: user source stack
grep -E 'ExceptionDebugDemo\.java:7' "$LOG" >/dev/null \
  || { echo "FAIL: throw site not in ExceptionDebugDemo.java:7"; exit 1; }
grep -E '_JN_ExceptionDebugDemo_deep|ExceptionDebugDemo\.java:9' "$LOG" >/dev/null \
  || { echo "FAIL: deep() frames missing at throw"; exit 1; }
grep -E 'jn_main|ExceptionDebugDemo\.java:13' "$LOG" >/dev/null \
  || { echo "FAIL: main frame missing at throw"; exit 1; }

# Unhandled stop
grep -E 'jnative_debugger_on_uncaught' "$LOG" >/dev/null \
  || { echo "FAIL: did not stop at uncaught hook"; exit 1; }

# Message + type printed after continue
grep -E 'RuntimeException' "$LOG" >/dev/null \
  || { echo "FAIL: exception type not printed"; exit 1; }
grep -E '调试未处理异常' "$LOG" >/dev/null \
  || { echo "FAIL: exception message not printed"; exit 1; }

grep -E 'exited with status = 1' "$LOG" >/dev/null \
  || { echo "FAIL: expected exit status 1 after uncaught"; exit 1; }

echo "PASS: unhandled exception stop + throw-site user stack verified"
echo "NOTE: CodeLLDB has no Java-style exception breakpoint API; use symbol breakpoints"
echo "NOTE: native SIGSEGV / abort remain distinct from JNative uncaught"
exit 0
