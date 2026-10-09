#!/usr/bin/env bash
# Author: 张晓达
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

# macOS .app bundle so Dock/open can focus the window
APP="$OUT.app"
mkdir -p "$APP/Contents/MacOS"
cp -f "$OUT" "$APP/Contents/MacOS/SudokuDesktop"
chmod +x "$APP/Contents/MacOS/SudokuDesktop"
cat > "$APP/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>CFBundleExecutable</key><string>SudokuDesktop</string>
  <key>CFBundleIdentifier</key><string>io.jnative.examples.sudoku.desktop</string>
  <key>CFBundleName</key><string>JNative Sudoku</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>NSHighResolutionCapable</key><true/>
</dict></plist>
PLIST
echo "Run: open $APP"
