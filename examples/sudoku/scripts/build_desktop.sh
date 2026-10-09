#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
OUT_DIR="$REPO/build/sudoku"
OUT="$OUT_DIR/SudokuDesktop"
MERGED="$OUT_DIR/merged-desktop.java"

mkdir -p "$OUT_DIR"
python3 - "$ROOT" "$MERGED" <<'PY'
from pathlib import Path
import sys
root, out = Path(sys.argv[1]), Path(sys.argv[2])
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
desk = root.joinpath("desktop/SudokuDesktop.java").read_text()
out.write_text("// AUTO-GENERATED\n" + "".join(body) + desk)
print(out)
PY

echo "==> desktop → $OUT"
java -jar "$JAR" "$MERGED" -o "$OUT" --app-type gui -O0
echo "Run: $OUT"
