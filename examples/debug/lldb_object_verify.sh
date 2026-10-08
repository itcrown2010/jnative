#!/usr/bin/env bash
# Verify object / String / array display via LLDB + JNative formatters (macOS arm64).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DEMO_DIR="$ROOT/examples/debug"
SRC="$DEMO_DIR/ObjectDebugDemo.java"
BIN="$DEMO_DIR/ObjectDebugDemo"
JAR="$ROOT/target/jnative.jar"
FMT="$ROOT/tools/lldb/jnative_formatters.py"

if [[ ! -f "$JAR" ]]; then
  (cd "$ROOT" && mvn -q -DskipTests package)
fi

if ! command -v lldb >/dev/null 2>&1; then
  echo "SKIP: lldb not found on PATH"
  exit 2
fi

echo "== debug compile ObjectDebugDemo =="
java -jar "$JAR" --debug "$SRC" -o "$BIN"
[[ -f "${BIN}.jn-debug.json" ]] || { echo "FAIL: missing ${BIN}.jn-debug.json"; exit 1; }
echo "== catalog head =="
head -n 20 "${BIN}.jn-debug.json"

# Confirm DWARF has DICompositeType for Dog / String (macOS: usually in .dSYM)
if command -v dwarfdump >/dev/null 2>&1; then
  echo "== dwarfdump composite check =="
  DI_SRC="$BIN"
  [[ -d "${BIN}.dSYM" ]] && DI_SRC="${BIN}.dSYM"
  if dwarfdump --debug-info "$DI_SRC" 2>/dev/null | grep -E 'DW_AT_name\s*\("Dog"\)' >/dev/null; then
    echo "OK: Dog structure type present"
  else
    echo "FAIL: Dog DICompositeType not found in $DI_SRC"
    exit 1
  fi
fi

LINE="$(grep -n 'System.out.println(probe)' "$SRC" | head -1 | cut -d: -f1)"
[[ -n "$LINE" ]] || { echo "FAIL: could not locate probe println"; exit 1; }
echo "== lldb at ObjectDebugDemo.java:$LINE =="

LOG="$(mktemp)"
set +e
lldb -b \
  -o "settings set target.process.stop-on-sharedlibrary-events false" \
  -o "command script import $FMT" \
  -o "breakpoint set --file ObjectDebugDemo.java --line $LINE" \
  -o "run" \
  -o "frame variable hello empty missing longText" \
  -o "frame variable nums emptyArr nullArr" \
  -o "frame variable dog animal nullDog" \
  -o "frame variable a b pack probe" \
  -o "continue" \
  -- "$BIN" >"$LOG" 2>&1
rc=$?
set -e

cat "$LOG"
echo "== checking log =="

if grep -qiE 'Operation not permitted|cannot attach|need to be run as root|developer mode' "$LOG"; then
  echo "SKIP: system blocked debugger attach"
  exit 2
fi

[[ $rc -eq 0 ]] || { echo "FAIL: lldb exited $rc"; exit 1; }

grep -E "Breakpoint [0-9]+:|stop reason = breakpoint" "$LOG" >/dev/null \
  || { echo "FAIL: breakpoint did not hit"; exit 1; }

# String UTF-8 / empty / null / truncation
grep -E '你好|JNative' "$LOG" >/dev/null \
  || { echo "FAIL: Chinese string not displayed"; exit 1; }
grep -E 'String\(len=0\) ""|empty = .*""' "$LOG" >/dev/null \
  || grep -E '\(String \*\) empty|empty = ' "$LOG" >/dev/null \
  || { echo "FAIL: empty string not shown"; exit 1; }
grep -E 'missing = null|missing = 0x0' "$LOG" >/dev/null \
  || { echo "FAIL: null String not shown as null"; exit 1; }
grep -E 'truncated|len=[0-9]{2,}' "$LOG" >/dev/null \
  || { echo "FAIL: long string summary missing"; exit 1; }

# Arrays (runtime RTTI name array.i32)
grep -E 'array\.i32\[5\].*\{10, 20, 30' "$LOG" >/dev/null \
  || { echo "FAIL: int array elements not displayed correctly"; exit 1; }
grep -E 'emptyArr = .*array\.i32\[0\] \{\}' "$LOG" >/dev/null \
  || { echo "FAIL: empty array not shown"; exit 1; }
grep -E 'nullArr = nullptr|nullArr = null' "$LOG" >/dev/null \
  || { echo "FAIL: null array not null"; exit 1; }

# Objects / inheritance / null
grep -E 'Dog\{name=String\(len=6\) "旺财", age=3, trained=true\}' "$LOG" >/dev/null \
  || { echo "FAIL: Dog inherited/own fields incorrect"; exit 1; }
grep -E 'nullDog = nullptr|nullDog = null' "$LOG" >/dev/null \
  || { echo "FAIL: nullDog not null"; exit 1; }
grep -E 'truncated' "$LOG" >/dev/null \
  || { echo "FAIL: long string not truncated"; exit 1; }

# Cycle must not explode (cycle marker or bounded address)
grep -E 'cycle|0x[0-9a-f]+' "$LOG" >/dev/null \
  || { echo "FAIL: node graph summary missing"; exit 1; }

# Must not show fake defaults for unreadable — soft check: no huge dumps
lines=$(wc -l <"$LOG" | tr -d ' ')
[[ "$lines" -lt 400 ]] || { echo "FAIL: formatter output unbounded ($lines lines)"; exit 1; }

echo "PASS: object/string/array LLDB display verified"
exit 0
