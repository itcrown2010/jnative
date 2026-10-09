#!/usr/bin/env bash
# CalculatorNativeE2E — verify AOT native binary (not a JVM wrapper).
# Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
BIN="${1:-$ROOT/build/calculator/Calculator}"
APP="${2:-$ROOT/build/calculator/Calculator.app}"

echo "== file =="
file "$BIN" | tee /tmp/calc-e2e-file.txt
grep -q "Mach-O 64-bit executable arm64" /tmp/calc-e2e-file.txt

echo "== no JVM link =="
otool -L "$BIN" | tee /tmp/calc-e2e-otool.txt
if grep -qiE 'libjvm|JavaVM\.framework' /tmp/calc-e2e-otool.txt; then
  echo "FAIL: JVM linkage detected"
  exit 1
fi
echo "NO_JVM_LINK"

echo "== Cocoa / AppKit =="
grep -q AppKit /tmp/calc-e2e-otool.txt

echo "== smoke launch =="
"$BIN" >/tmp/calc-e2e-out.txt 2>/tmp/calc-e2e-err.txt &
PID=$!
sleep 2
if ! kill -0 "$PID" 2>/dev/null; then
  echo "FAIL: GUI exited early"
  cat /tmp/calc-e2e-err.txt
  exit 1
fi
kill "$PID" 2>/dev/null || true
wait "$PID" 2>/dev/null || true
echo "GUI_SMOKE_OK"

if [[ -x "$APP/Contents/MacOS/Calculator" ]]; then
  echo "== .app bundle =="
  "$APP/Contents/MacOS/Calculator" >/tmp/calc-e2e-app-out.txt 2>/tmp/calc-e2e-app-err.txt &
  APID=$!
  sleep 2
  if ! kill -0 "$APID" 2>/dev/null; then
    echo "FAIL: .app exited early"
    cat /tmp/calc-e2e-app-err.txt
    exit 1
  fi
  kill "$APID" 2>/dev/null || true
  wait "$APID" 2>/dev/null || true
  echo "APP_SMOKE_OK"
fi

echo "CalculatorNativeE2E PASS"
