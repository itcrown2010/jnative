#!/usr/bin/env bash
# Author: 张晓达
# Build Wonder Campus iOS shell via swiftc packaging (independent of calculator).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_ROOT="$ROOT/WonderCampus"
OUT_APP="$ROOT/build/WonderCampus.app"
WC_ENV_BUILD="${WC_ENV:-dev}"
"$ROOT/scripts/sync_www.sh"
mkdir -p "$ROOT/build" "$OUT_APP"

SDK=$(xcrun --sdk iphonesimulator --show-sdk-path)
TARGET="arm64-apple-ios17.0-simulator"
printf 'APPL????' > "$OUT_APP/PkgInfo"
cp "$APP_ROOT/Info.plist" "$OUT_APP/Info.plist"
/usr/libexec/PlistBuddy -c "Set :CFBundleExecutable WonderCampus" "$OUT_APP/Info.plist" 2>/dev/null \
  || /usr/libexec/PlistBuddy -c "Add :CFBundleExecutable string WonderCampus" "$OUT_APP/Info.plist"

# Bundle env config (dev/test/prod). Override identifier for local teams if needed.
case "$WC_ENV_BUILD" in
  prod|production) CONFIG_PLIST="Config.prod.plist" ;;
  test) CONFIG_PLIST="Config.test.plist" ;;
  *) CONFIG_PLIST="Config.dev.plist" ;;
esac
cp "$APP_ROOT/$CONFIG_PLIST" "$OUT_APP/$CONFIG_PLIST"
cp "$APP_ROOT/Config.dev.plist" "$OUT_APP/Config.dev.plist"
cp "$APP_ROOT/Config.test.plist" "$OUT_APP/Config.test.plist"
cp "$APP_ROOT/Config.prod.plist" "$OUT_APP/Config.prod.plist"

# Optional: WC_BUNDLE_ID=com.yourteam.wondercampus
if [[ -n "${WC_BUNDLE_ID:-}" ]]; then
  /usr/libexec/PlistBuddy -c "Set :CFBundleIdentifier $WC_BUNDLE_ID" "$OUT_APP/Info.plist"
fi

# Prod builds strip cleartext ATS exceptions (HTTPS only).
if [[ "$WC_ENV_BUILD" == "prod" || "$WC_ENV_BUILD" == "production" ]]; then
  /usr/libexec/PlistBuddy -c "Delete :NSAppTransportSecurity" "$OUT_APP/Info.plist" 2>/dev/null || true
  /usr/libexec/PlistBuddy -c "Add :NSAppTransportSecurity dict" "$OUT_APP/Info.plist"
  /usr/libexec/PlistBuddy -c "Add :NSAppTransportSecurity:NSAllowsArbitraryLoads bool false" "$OUT_APP/Info.plist"
fi

mkdir -p "$OUT_APP/www"
cp -R "$APP_ROOT/www/"* "$OUT_APP/www/"

# Simple generated icon (1024→AppIcon) — solid warm mark, no child PII
ICON_DIR="$OUT_APP"
python3 - <<'PY' "$ICON_DIR" 2>/dev/null || true
import struct, zlib, sys, os
out = os.path.join(sys.argv[1], "AppIcon60x60@2x.png")
w = h = 120
def chunk(t, d):
    return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
raw = b''
for y in range(h):
    raw += b'\x00'
    for x in range(w):
        # warm peach circle-ish
        cx, cy = w/2, h/2
        d = ((x-cx)**2 + (y-cy)**2) ** 0.5
        if d < 48:
            raw += bytes([224, 122, 58, 255])
        elif d < 56:
            raw += bytes([58, 143, 122, 255])
        else:
            raw += bytes([255, 247, 239, 255])
open(out,'wb').write(
    b'\x89PNG\r\n\x1a\n' +
    chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) +
    chunk(b'IDAT', zlib.compress(raw, 9)) +
    chunk(b'IEND', b'')
)
print('icon', out)
PY
# Icon PNG is copied beside Info.plist; omit CFBundleIcons to avoid install promise issues on some sim runtimes.

xcrun --sdk iphonesimulator swiftc \
  -target "$TARGET" \
  -sdk "$SDK" \
  -O \
  -parse-as-library \
  -o "$OUT_APP/WonderCampus" \
  "$APP_ROOT/AppDelegate.swift" \
  "$APP_ROOT/KeychainStore.swift" \
  "$APP_ROOT/ViewController.swift" \
  -framework UIKit -framework WebKit -framework Security

codesign --force --sign - "$OUT_APP" >/dev/null 2>&1 || true
echo "Built $OUT_APP (WC_ENV=$WC_ENV_BUILD)"
echo "Install: xcrun simctl install booted \"$OUT_APP\""
echo "API: WC_API_BASE (sim default http://127.0.0.1:18880). Device: Mac LAN IP — never device 127.0.0.1."
