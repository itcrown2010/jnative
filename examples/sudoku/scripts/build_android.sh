#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -z "${ANDROID_HOME:-}" && -d "$HOME/Library/Android/sdk" ]]; then
  export ANDROID_HOME="$HOME/Library/Android/sdk"
fi
if [[ -n "${ANDROID_HOME:-}" ]]; then
  export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
fi
"$ROOT/scripts/sync_mobile_assets.sh"
cd "$ROOT/mobile/android"
./gradlew assembleDebug
APK="$ROOT/mobile/android/app/build/outputs/apk/debug/app-debug.apk"
echo "APK: $APK (build≠device run; install/play is a separate verification step)"
ls -la "$APK"
