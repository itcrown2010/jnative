#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID="$ROOT/android"

"$ROOT/scripts/build_web.sh"
"$ROOT/scripts/sync_mobile_assets.sh"

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
if [[ ! -d "$ANDROID_HOME" ]]; then
  echo "BLOCKED: Android SDK not found (set ANDROID_HOME)." >&2
  echo "Project is at $ANDROID — build skipped." >&2
  exit 2
fi
export PATH="$ANDROID_HOME/platform-tools:$PATH"

cd "$ANDROID"
if [[ -x ./gradlew ]]; then
  ./gradlew :app:assembleDebug
else
  if ! command -v gradle >/dev/null; then
    echo "BLOCKED: gradle / gradlew not available." >&2
    exit 2
  fi
  gradle :app:assembleDebug
fi

APK="$ANDROID/app/build/outputs/apk/debug/app-debug.apk"
echo "Android APK: $APK"
