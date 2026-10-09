#!/usr/bin/env bash
# Author: 张晓达
# Install + launch the built simulator .app and capture screenshots when possible.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/build/IosCalculator.app"
BUNDLE_ID="io.jnative.examples.mobile.ioscalculator"
SHOTS="$ROOT/screenshots"

if [[ ! -d "$APP" ]]; then
  echo "error: missing $APP — run scripts/build_ios.sh first" >&2
  exit 1
fi

if ! command -v xcrun >/dev/null; then
  echo "BLOCKED: xcrun not found" >&2
  exit 2
fi

# Pick a booted simulator or boot the first available iPhone
UDID="$(xcrun simctl list devices booted | awk -F '[()]' '/iPhone/{print $2; exit}')"
if [[ -z "${UDID:-}" ]]; then
  UDID="$(xcrun simctl list devices available | awk -F '[()]' '/iPhone/{print $2; exit}')"
  if [[ -z "${UDID:-}" ]]; then
    echo "BLOCKED: no available iPhone simulator runtime" >&2
    exit 2
  fi
  echo "==> boot $UDID"
  xcrun simctl boot "$UDID" || true
  xcrun simctl bootstatus "$UDID" -b || true
fi

echo "==> install $APP → $UDID"
xcrun simctl install "$UDID" "$APP"
mkdir -p "$SHOTS"

echo "==> launch (clean)"
xcrun simctl launch "$UDID" "$BUNDLE_ID"
sleep 2
xcrun simctl io "$UDID" screenshot "$SHOTS/01-launch.png"
echo "==> screenshot $SHOTS/01-launch.png"

echo "==> launch with -autodemo (clicks 12 + 30 = via real buttons)"
xcrun simctl terminate "$UDID" "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl launch "$UDID" "$BUNDLE_ID" -autodemo
sleep 5
xcrun simctl io "$UDID" screenshot "$SHOTS/02-12-plus-30.png"
echo "==> screenshot $SHOTS/02-12-plus-30.png"

echo "Simulator running ($UDID). Manual taps also work on the keypad."
