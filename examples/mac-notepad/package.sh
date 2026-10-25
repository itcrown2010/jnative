#!/bin/sh
# Builds the notepad and wraps it into MacNotepad.app (double-clickable).
#   ./package.sh            uses ../../target/classes (or $JNATIVE_COMPILER)
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
COMPILER="${JNATIVE_COMPILER:-$ROOT/target/classes}"
cd "$HERE"
if [ -d "$COMPILER" ]; then
    java -cp "$COMPILER" io.jnative.Main build --project .
else
    java -jar "$COMPILER" build --project .
fi
APP="$HERE/MacNotepad.app"
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS"
cp .jnative/build/MacNotepad "$APP/Contents/MacOS/MacNotepad"
cat > "$APP/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleName</key><string>记事本</string>
    <key>CFBundleDisplayName</key><string>记事本</string>
    <key>CFBundleIdentifier</key><string>io.jnative.macnotepad</string>
    <key>CFBundleExecutable</key><string>MacNotepad</string>
    <key>CFBundlePackageType</key><string>APPL</string>
    <key>CFBundleShortVersionString</key><string>1.0</string>
    <key>LSMinimumSystemVersion</key><string>11.0</string>
    <key>NSHighResolutionCapable</key><true/>
</dict>
</plist>
PLIST
echo "Built $APP"
