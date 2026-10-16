#!/usr/bin/env bash
# Author: 张晓达
# Sync touch shell into independent Wonder Campus iOS/Android trees only (not calculator).
set -euo pipefail
WC="$(cd "$(dirname "$0")/.." && pwd)/mobile"
chmod +x "$WC/android/scripts/sync_www.sh" "$WC/ios/scripts/sync_www.sh"
"$WC/android/scripts/sync_www.sh"
"$WC/ios/scripts/sync_www.sh"
echo "UI path: WebView shell only (not native UIKit/Compose)"
