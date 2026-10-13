#!/usr/bin/env bash
# Pack a macOS .app from a built Mach-O GUI binary.
# Author: 张晓达
# Does NOT code-sign or notarize — local runnable only.
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/macos/pack-app.sh --bin PATH --name NAME --id BUNDLE_ID [--out DIR] [--version VER] [--icon ICNS]

  --bin       Built executable (absolute or relative)
  --name      CFBundleName / display name (also executable name inside .app)
  --id        CFBundleIdentifier (e.g. io.jnative.examples.product-editor)
  --out       Output directory for Name.app (default: dirname of --bin)
  --version   CFBundleShortVersionString (default: 0.1.0)
  --icon      Optional .icns path copied to Contents/Resources/AppIcon.icns

User data must NOT be written inside the .app; use Files.configDir(appName).
EOF
}

BIN=""
NAME=""
ID=""
OUT=""
VER="0.1.0"
ICON=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --bin) BIN="${2:-}"; shift 2 ;;
    --name) NAME="${2:-}"; shift 2 ;;
    --id) ID="${2:-}"; shift 2 ;;
    --out) OUT="${2:-}"; shift 2 ;;
    --version) VER="${2:-}"; shift 2 ;;
    --icon) ICON="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "$BIN" || -z "$NAME" || -z "$ID" ]]; then
  usage
  exit 2
fi
if [[ ! -f "$BIN" ]]; then
  echo "error: binary not found: $BIN" >&2
  exit 1
fi
if [[ -z "$OUT" ]]; then
  OUT="$(cd "$(dirname "$BIN")" && pwd)"
fi
mkdir -p "$OUT"
APP="$OUT/${NAME}.app"
# Fresh bundle — never launch a stale Contents/MacOS binary from a failed build.
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp -f "$BIN" "$APP/Contents/MacOS/$NAME"
chmod +x "$APP/Contents/MacOS/$NAME"
if [[ -n "$ICON" && -f "$ICON" ]]; then
  cp -f "$ICON" "$APP/Contents/Resources/AppIcon.icns"
fi

ICON_KEYS=""
if [[ -f "$APP/Contents/Resources/AppIcon.icns" ]]; then
  ICON_KEYS=$'\n  <key>CFBundleIconFile</key><string>AppIcon</string>'
fi

cat > "$APP/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>CFBundleExecutable</key><string>${NAME}</string>
  <key>CFBundleIdentifier</key><string>${ID}</string>
  <key>CFBundleName</key><string>${NAME}</string>
  <key>CFBundleDisplayName</key><string>${NAME}</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleShortVersionString</key><string>${VER}</string>
  <key>CFBundleVersion</key><string>${VER}</string>
  <key>LSMinimumSystemVersion</key><string>11.0</string>
  <key>NSHighResolutionCapable</key><true/>
  <key>NSSupportsAutomaticGraphicsSwitching</key><true/>${ICON_KEYS}
</dict></plist>
PLIST

echo "OK $APP"
echo "Run: open \"$APP\""
echo "Note: unsigned local build — not for public distribution / notarization."
