#!/usr/bin/env bash
# Author: 张晓达
# JNative Windows Explorer build.
#
# - On Windows (Git Bash / MSYS2): compiles native/jn_fm_windows.c with clang, then AOT-compiles the
#   Java sources into WindowsExplorer.exe (native Win32 GUI, no JRE at runtime).
# - On macOS/Linux: MinGW compile-checks jn_fm_windows.c and builds a host proxy using the POSIX stand-in
#   tests/jn_fm_posix.c ("C:\..." is mapped onto $JNFM_ROOT). A real .exe needs a Windows host.
#
# Status: WINDOWS_E2E_UNVERIFIED until WindowsExplorer.exe is run on Windows 10/11.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
OUT_DIR="${OUT_DIR:-$REPO/build/windows-explorer}"
mkdir -p "$OUT_DIR"

if [[ ! -f "$JAR" ]]; then
  echo "Missing $JAR - run: mvn -DskipTests package" >&2
  exit 1
fi

SOURCES=()
for f in "$ROOT"/src/*.java; do
  SOURCES+=("$f")
done

OS="$(uname -s 2>/dev/null || echo unknown)"
if [[ "$OS" == MINGW* || "$OS" == MSYS* || "$OS" == CYGWIN* || "$OS" == Windows_NT ]]; then
  CC="${CC:-clang}"
  echo "==> compile jn_fm_windows.c ($CC)"
  "$CC" -c -O2 -I"$ROOT/native" "$ROOT/native/jn_fm_windows.c" -o "$OUT_DIR/jn_fm_windows.o"
  rm -f "$OUT_DIR/libjn_fm.a"
  ar rcs "$OUT_DIR/libjn_fm.a" "$OUT_DIR/jn_fm_windows.o"
  OUT="$OUT_DIR/WindowsExplorer.exe"
  echo "==> jnative AOT -> $OUT (native Win32 GUI, no JRE at runtime)"
  java -jar "$JAR" "${SOURCES[@]}" -o "$OUT" --app-type gui -O1 \
    -L "$OUT_DIR" -l jn_fm -l shell32 -l ole32 -l user32 -l advapi32
  echo "OK $OUT"
  echo "Status: WINDOWS_E2E_UNVERIFIED"
  exit 0
fi

# MinGW compile-check of the Windows shell layer (object only).
if command -v x86_64-w64-mingw32-gcc >/dev/null 2>&1; then
  echo "==> MinGW compile-check jn_fm_windows.c"
  x86_64-w64-mingw32-gcc -c -O2 -Wall -I"$ROOT/native" "$ROOT/native/jn_fm_windows.c" \
    -o "$OUT_DIR/jn_fm_windows.o"
  echo "OK $OUT_DIR/jn_fm_windows.o"
fi
WIN_UI="$REPO/src/main/resources/native/windows/jnative_ui_win32.c"
if [[ -f "$WIN_UI" ]] && command -v x86_64-w64-mingw32-gcc >/dev/null 2>&1; then
  echo "==> MinGW compile-check jnative_ui_win32.c"
  x86_64-w64-mingw32-gcc -c -O2 -I"$REPO/src/main/resources/native/windows" \
    -I"$REPO/src/main/resources/native/posix" "$WIN_UI" -o "$OUT_DIR/jnative_ui_win32.o"
  echo "OK $OUT_DIR/jnative_ui_win32.o"
fi

# Host proxy against the POSIX stand-in (UI iteration only).
echo "==> host proxy (POSIX stand-in for jn_fm)"
clang -c -O1 "$ROOT/tests/jn_fm_posix.c" -o "$OUT_DIR/jn_fm_posix.o"
rm -f "$OUT_DIR/libjn_fm.a"
ar rcs "$OUT_DIR/libjn_fm.a" "$OUT_DIR/jn_fm_posix.o"
OUT="$OUT_DIR/WindowsExplorer"
java -jar "$JAR" "${SOURCES[@]}" -o "$OUT" --app-type gui -O1 -L "$OUT_DIR" -l jn_fm
echo "OK $OUT   (run with: JNFM_ROOT=/some/dir $OUT)"
echo ""
echo "To get WindowsExplorer.exe: powershell -File examples/windows-explorer/scripts/build.ps1"
echo "WINDOWS_E2E_UNVERIFIED: the .exe must be built and run on Windows 10/11 x64"
