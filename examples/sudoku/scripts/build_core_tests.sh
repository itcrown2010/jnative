#!/usr/bin/env bash
# Author: 张晓达
# Concatenate SudokuCore + tests → native headless binary.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
OUT_DIR="$REPO/build/sudoku"
OUT="$OUT_DIR/SudokuCoreTests"
MERGED="$OUT_DIR/merged-tests.java"
LOG="$OUT_DIR/tests.out"

if [[ ! -f "$JAR" ]]; then
  echo "error: jnative jar not found at $JAR" >&2
  exit 1
fi

mkdir -p "$OUT_DIR"
python3 - "$ROOT" "$MERGED" <<'PY'
from pathlib import Path
import sys
root = Path(sys.argv[1])
out = Path(sys.argv[2])
core = root.joinpath("core/SudokuCore.java").read_text()
core = core.replace("public class SudokuCore", "class SudokuCore", 1)
lines = core.splitlines(True)
body = []
i = 0
while i < len(lines):
    if lines[i].startswith("    public static void main(String[] args)"):
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
tests = root.joinpath("tests/SudokuCoreTests.java").read_text()
out.write_text("// AUTO-GENERATED — do not edit\n" + "".join(body) + tests)
print("merged", out)
PY

echo "==> compiling core tests → $OUT"
java -jar "$JAR" "$MERGED" -o "$OUT"
echo "==> running $OUT"
"$OUT" | tee "$LOG"
if grep -qE "FAIL_EXIT|failed=[1-9]" "$LOG"; then
  echo "error: core tests failed" >&2
  exit 1
fi
echo "ALL CORE TESTS PASSED"
