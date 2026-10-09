#!/usr/bin/env bash
# Author: 张晓达
# Install Sudoku on a connected physical iPhone (requires valid Xcode Apple ID login).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/scripts/sync_mobile_assets.sh"
cd "$ROOT/mobile/ios"

TEAM="${DEVELOPMENT_TEAM:-8F86W57524}"
# Prefer first available paired physical iPhone
DEVICE_UDID="${IOS_DEVICE_UDID:-}"
if [[ -z "$DEVICE_UDID" ]]; then
  DEVICE_UDID=$(xcrun xctrace list devices 2>/dev/null | awk -F'[()]' '/iphone|iPhone/{gsub(/ /,"",$2); if($2 ~ /^0000/){print $2; exit}}')
fi
if [[ -z "$DEVICE_UDID" ]]; then
  DEVICE_UDID=$(xcrun devicectl list devices 2>/dev/null | awk '/available \(paired\)/{print}' | head -1)
  # fallback known device from this machine
  DEVICE_UDID="${DEVICE_UDID:-00008140-0001382A3CA2801C}"
fi

echo "==> device UDID: $DEVICE_UDID"
echo "==> team: $TEAM"

xcodebuild -project Calculator.xcodeproj -scheme Calculator \
  -destination "platform=iOS,id=$DEVICE_UDID" \
  -configuration Debug \
  -allowProvisioningUpdates \
  -allowProvisioningDeviceRegistration \
  DEVELOPMENT_TEAM="$TEAM" \
  CODE_SIGN_STYLE=Automatic \
  build

APP=$(ls -d "$HOME"/Library/Developer/Xcode/DerivedData/Calculator-*/Build/Products/Debug-iphoneos/Calculator.app | head -1)
echo "==> app: $APP"
xcrun devicectl device install app --device "$DEVICE_UDID" "$APP"
xcrun devicectl device process launch --device "$DEVICE_UDID" io.jnative.examples.mobile.sudoku
echo "==> installed & launched JNative Sudoku"
