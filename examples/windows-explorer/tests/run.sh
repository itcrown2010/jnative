#!/usr/bin/env bash
# Author: 张晓达
# Headless logic tests for the JNative Windows Explorer (macOS / Linux).
# The native shell layer is replaced by tests/jn_fm_posix.c, which maps "C:\..." onto $JNFM_ROOT.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
OUT="${OUT_DIR:-$REPO/build/windows-explorer-tests}"
mkdir -p "$OUT"
export JNFM_ROOT="$OUT/root"
rm -rf "$JNFM_ROOT" "${JNFM_ROOT}_RECYCLE"
mkdir -p "$JNFM_ROOT/Users/me/Documents/Archive/sub" "$JNFM_ROOT/Users/me/Documents/Notes" \
  "$JNFM_ROOT/Users/me/Desktop" "$JNFM_ROOT/Users/me/Downloads" "$JNFM_ROOT/Users/me/Pictures" \
  "$JNFM_ROOT/Users/me/Music" "$JNFM_ROOT/Users/me/Videos" "$JNFM_ROOT/Program Files"
cd "$JNFM_ROOT/Users/me/Documents"
echo a > alpha.txt
echo b > beta.md
echo c > "report 2.docx"
echo d > "report 10.docx"
echo h > .hidden
echo t > Notes/todo.txt
touch -t 202501010101 alpha.txt

clang -c -O1 -Wall "$ROOT/tests/jn_fm_posix.c" -o "$OUT/jn_fm_posix.o"
rm -f "$OUT/libjn_fm.a"
ar rcs "$OUT/libjn_fm.a" "$OUT/jn_fm_posix.o"

SRCS=()
for f in "$ROOT"/src/*.java; do
  case "$f" in */WindowsExplorer.java) ;; *) SRCS+=("$f") ;; esac
done
java -jar "$JAR" "${SRCS[@]}" "$ROOT/tests/FmTests.java" -o "$OUT/fm-tests" --app-type gui -O1 -L "$OUT" -l jn_fm
perl -e 'alarm 120; exec @ARGV' "$OUT/fm-tests"
