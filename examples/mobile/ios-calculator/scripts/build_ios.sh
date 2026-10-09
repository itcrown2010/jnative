#!/usr/bin/env bash
# Author: 张晓达
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IOS="$ROOT/ios"
OUT_APP="$ROOT/build/IosCalculator.app"

"$ROOT/scripts/build_web.sh"
"$ROOT/scripts/sync_ios_assets.sh"

if ! command -v xcodebuild >/dev/null; then
  echo "BLOCKED: xcodebuild not found." >&2
  exit 2
fi

cd "$IOS"

CANDIDATES=()
if [[ -n "${IOS_DESTINATION:-}" ]]; then
  CANDIDATES+=("$IOS_DESTINATION")
fi
CANDIDATES+=(
  "platform=iOS Simulator,OS=17.5,name=iPhone 15"
  "platform=iOS Simulator,OS=17.5,name=iPhone SE (3rd generation)"
  "platform=iOS Simulator,OS=17.5,name=iPhone 15 Pro"
  "generic/platform=iOS Simulator"
  "platform=iOS Simulator,name=iPhone 16"
  "platform=iOS Simulator,name=iPhone 15"
  "platform=iOS Simulator,name=iPhone 14"
)

ok=0
DERIVED="$ROOT/build/DerivedData"
rm -rf "$DERIVED"
mkdir -p "$DERIVED" "$ROOT/build"

for DEST in "${CANDIDATES[@]}"; do
  echo "==> trying destination: $DEST"
  if xcodebuild -project IosCalculator.xcodeproj -scheme IosCalculator \
      -destination "$DEST" \
      -configuration Debug \
      -derivedDataPath "$DERIVED" \
      CODE_SIGNING_ALLOWED=NO \
      CODE_SIGN_IDENTITY="" \
      build; then
    echo "iOS build finished (destination: $DEST)"
    ok=1
    break
  fi
done

if [[ "$ok" -eq 1 ]]; then
  APP="$(find "$DERIVED" -name 'IosCalculator.app' -type d | head -n 1 || true)"
  if [[ -z "$APP" || ! -d "$APP" ]]; then
    echo "error: built .app not found under $DERIVED" >&2
    exit 1
  fi
  rm -rf "$OUT_APP"
  cp -R "$APP" "$OUT_APP"
  echo "==> simulator app: $OUT_APP"
  exit 0
fi

echo "WARN: xcodebuild destinations unavailable — falling back to swiftc + .app bundle" >&2
SDK="$(xcrun --sdk iphonesimulator --show-sdk-path 2>/dev/null || true)"
if [[ -z "$SDK" ]]; then
  echo "BLOCKED: no iphonesimulator SDK" >&2
  exit 2
fi

SRC="$IOS/IosCalculator"
mkdir -p "$OUT_APP"
# Minimal Info.plist
cp "$SRC/Info.plist" "$OUT_APP/Info.plist"
/usr/libexec/PlistBuddy -c "Set :CFBundleExecutable IosCalculator" "$OUT_APP/Info.plist" 2>/dev/null || \
  /usr/libexec/PlistBuddy -c "Add :CFBundleExecutable string IosCalculator" "$OUT_APP/Info.plist"
/usr/libexec/PlistBuddy -c "Set :CFBundleIdentifier io.jnative.examples.mobile.ioscalculator" "$OUT_APP/Info.plist" 2>/dev/null || true
printf 'APPL????' > "$OUT_APP/PkgInfo"
rm -rf "$OUT_APP/www"
cp -R "$SRC/www" "$OUT_APP/www"

xcrun --sdk iphonesimulator swiftc \
  -target arm64-apple-ios16.0-simulator \
  -sdk "$SDK" \
  -O \
  -parse-as-library \
  -o "$OUT_APP/IosCalculator" \
  "$SRC/AppDelegate.swift" \
  "$SRC/ViewController.swift" \
  "$SRC/LocalSchemeHandler.swift" \
  -framework UIKit -framework WebKit

codesign --force --sign - "$OUT_APP" >/dev/null
echo "PARTIAL/OK: swiftc simulator app at $OUT_APP"
echo "==> simulator app: $OUT_APP"
