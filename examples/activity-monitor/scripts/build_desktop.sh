#!/usr/bin/env bash
# Author: 张晓达
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
OUT_DIR="${OUT_DIR:-$REPO/build/activity-monitor}"
OUT="$OUT_DIR/ActivityMonitor"
MERGED="$OUT_DIR/merged-desktop.java"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "Activity Monitor example currently supports macOS only." >&2
  exit 1
fi

"$ROOT/scripts/build_native.sh"

mkdir -p "$OUT_DIR"
python3 - "$ROOT" "$MERGED" <<'PY'
from pathlib import Path
import sys
root, out = Path(sys.argv[1]), Path(sys.argv[2])
core = root.joinpath("core/AmCore.java").read_text()
core = core.replace("public class AmCore", "class AmCore", 1)
# Strip AmCore.main so ActivityMonitor is the sole entry; keep runCli.
lines = core.splitlines(True)
body = []
i = 0
while i < len(lines):
    if "public static void main(String[] args)" in lines[i] and "AmCore" not in lines[i]:
        # match AmCore.main method body only (next lines until closing brace of main)
        pass
    if lines[i].strip() == "public static void main(String[] args) {":
        # skip main that only forwards to runCli
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
desk = root.joinpath("desktop/ActivityMonitor.java").read_text()
out.write_text("// AUTO-GENERATED — activity-monitor desktop\n" + "".join(body) + "\n" + desk)
print(out)
PY

echo "==> desktop → $OUT"
java -jar "$JAR" "$MERGED" -o "$OUT" --app-type gui -O0 \
  -L "$OUT_DIR" -l jn_am
echo "Run: $OUT"
echo "Dump: $OUT --dump"
