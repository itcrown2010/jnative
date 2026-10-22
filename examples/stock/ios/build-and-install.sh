#!/bin/sh
# Rebuild the iOS app and install it on a paired iPhone.
# Usage: ./build-and-install.sh <CoreDevice id from `xcrun devicectl list devices`>
set -e
cd "$(dirname "$0")"
xcodegen generate
xcodebuild -project StockLab.xcodeproj -scheme StockLab -configuration Release -destination 'generic/platform=iOS' \
  -derivedDataPath /tmp/stocklab-dev -allowProvisioningUpdates build
xcrun devicectl device install app --device "$1" /tmp/stocklab-dev/Build/Products/Release-iphoneos/StockLab.app
xcrun devicectl device process launch --device "$1" com.zhangxiaoda.stocklab
