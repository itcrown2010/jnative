#!/usr/bin/env bash
# Author: 张晓达
# JNative Windows Notepad build.
#
# - On Windows (Git Bash / MSYS2 / PowerShell via build.ps1): produces Notepad.exe
#   — a native Win32 GUI binary. Runtime needs NO Java/JRE.
# - On macOS/Linux: builds a host-native GUI proxy (Cocoa) for UI iteration, and
#   optionally MinGW-compiles jnative_ui_win32.c as a Windows ABI smoke check.
#   Full .exe linking requires a Windows host (no cross PE link from macOS yet).
#
# Status: WINDOWS_E2E_UNVERIFIED until Notepad.exe is run on Windows 10/11.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
OUT_DIR="${OUT_DIR:-$REPO/build/windows-notepad}"
SRC="$ROOT/src"
mkdir -p "$OUT_DIR"

if [[ ! -f "$JAR" ]]; then
  echo "Missing $JAR — run: mvn -DskipTests package" >&2
  exit 1
fi

SOURCES=(
  "$SRC/NpCodec.java"
  "$SRC/NpUi.java"
  "$SRC/NpFindDialog.java"
  "$SRC/NpGotoDialog.java"
  "$SRC/NpWindow.java"
  "$SRC/NpApp.java"
)

OS="$(uname -s 2>/dev/null || echo unknown)"
if [[ "$OS" == MINGW* || "$OS" == MSYS* || "$OS" == CYGWIN* || "$OS" == Windows_NT ]]; then
  OUT="$OUT_DIR/Notepad.exe"
  echo "==> jnative AOT → $OUT (native Win32 GUI, no JRE at runtime)"
  java -jar "$JAR" "${SOURCES[@]}" -o "$OUT" --app-type gui -O1
  echo "OK $OUT"
  echo "Run (no Java): $OUT"
  echo "Status: WINDOWS_E2E_UNVERIFIED"
  exit 0
fi

# Host proxy (macOS/Linux) — same sources, native GUI binary for the current OS.
OUT="$OUT_DIR/Notepad"
echo "==> jnative AOT → $OUT (host GUI proxy; not a JVM program)"
java -jar "$JAR" "${SOURCES[@]}" -o "$OUT" --app-type gui -O1
echo "OK $OUT (file(1) should say Mach-O / ELF, not JAR)"
file "$OUT" || true

# Optional: MinGW compile-check of the Win32 UI backend (object only).
WIN_UI="$REPO/src/main/resources/native/windows/jnative_ui_win32.c"
if [[ -f "$WIN_UI" ]] && command -v x86_64-w64-mingw32-gcc >/dev/null 2>&1; then
  echo "==> MinGW compile-check jnative_ui_win32.c"
  x86_64-w64-mingw32-gcc -c -O2 -I"$REPO/src/main/resources/native/windows" \
    -I"$REPO/src/main/resources/native/posix" \
    "$WIN_UI" -o "$OUT_DIR/jnative_ui_win32.o"
  echo "OK $OUT_DIR/jnative_ui_win32.o"
fi

echo ""
echo "To get Notepad.exe (double-click on Windows, no Java):"
echo "  powershell -File examples/windows-notepad/scripts/build.ps1"
echo "WINDOWS_E2E_UNVERIFIED: .exe must be built and run on Windows 10/11 x64"
