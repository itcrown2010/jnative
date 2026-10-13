#!/usr/bin/env bash
# Author: 张晓达
# Windows host build for JNative Task Monitor (Git Bash / MSYS2 / MinGW).
# On macOS/Linux this script only cross-compiles jn_am_windows.c (no EXE).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
OUT_DIR="${OUT_DIR:-$REPO/build/windows-task-monitor}"
NATIVE="$(cd "$ROOT/../activity-monitor/native" && pwd)"
mkdir -p "$OUT_DIR"

OS="$(uname -s 2>/dev/null || echo unknown)"
if [[ "$OS" == MINGW* || "$OS" == MSYS* || "$OS" == CYGWIN* || "$OS" == Windows_NT ]]; then
  CC="${CC:-clang}"
  echo "==> compile jn_am_windows.c ($CC)"
  "$CC" -c -O2 -I"$NATIVE" "$NATIVE/jn_am_windows.c" -o "$OUT_DIR/jn_am_windows.o"
  rm -f "$OUT_DIR/libjn_am.a"
  ar rcs "$OUT_DIR/libjn_am.a" "$OUT_DIR/jn_am_windows.o"

  MERGED="$OUT_DIR/merged-task-monitor.java"
  python3 - "$ROOT" "$MERGED" <<'PY'
from pathlib import Path
import sys
root, out = Path(sys.argv[1]), Path(sys.argv[2])
core = (root.parent / "activity-monitor/core/AmCore.java").read_text()
core = core.replace("public class AmCore", "class AmCore", 1)
lines = core.splitlines(True)
body = []
i = 0
while i < len(lines):
    if lines[i].strip() == "public static void main(String[] args) {":
        depth = 0
        started = False
        while i < len(lines):
            for ch in lines[i]:
                if ch == "{":
                    depth += 1
                    started = True
                elif ch == "}":
                    depth -= 1
            i += 1
            if started and depth == 0:
                break
        continue
    body.append(lines[i])
    i += 1
desk = (root / "src/TaskMonitor.java").read_text()
out.write_text("// AUTO-GENERATED — windows-task-monitor\n" + "".join(body) + "\n" + desk)
print(out)
PY
  OUT="$OUT_DIR/jnative-task-monitor.exe"
  echo "==> desktop → $OUT"
  java -jar "$JAR" "$MERGED" -o "$OUT" --app-type gui -O1 \
    -L "$OUT_DIR" -l jn_am
  echo "OK $OUT"
  exit 0
fi

# Non-Windows: cross-compile sampling ABI only (evidence for CI / audit).
if command -v x86_64-w64-mingw32-gcc >/dev/null 2>&1; then
  echo "==> cross-compile jn_am_windows.c (MinGW)"
  x86_64-w64-mingw32-gcc -c -O2 -I"$NATIVE" "$NATIVE/jn_am_windows.c" \
    -o "$OUT_DIR/jn_am_windows.o"
  rm -f "$OUT_DIR/libjn_am.a"
  x86_64-w64-mingw32-ar rcs "$OUT_DIR/libjn_am.a" "$OUT_DIR/jn_am_windows.o"
  echo "OK $OUT_DIR/libjn_am.a (Windows object; EXE requires Windows host)"
  echo "WINDOWS_E2E_UNVERIFIED: run scripts/build.ps1 on Windows 10/11 x64"
  exit 0
fi

echo "Need Windows host (build.ps1) or x86_64-w64-mingw32-gcc for ABI compile." >&2
exit 1
