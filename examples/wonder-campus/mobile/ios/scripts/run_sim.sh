#!/usr/bin/env bash
# Author: 张晓达
# Boot simulator, install WonderCampus, launch against local wonder-server.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../../../.." && pwd)"
OUT_APP="$ROOT/build/WonderCampus.app"
DEVICE_NAME="${1:-WC-iPhone-15}"
BUNDLE_ID="${WC_BUNDLE_ID:-io.jnative.examples.wondercampus}"
API_BASE="${WC_API_BASE:-http://127.0.0.1:18880}"
SHOT_DIR="${WC_SHOT_DIR:-$REPO/build/wonder-campus/ios-shots}"
mkdir -p "$SHOT_DIR"

"$ROOT/scripts/build_ios.sh"

UDID=$(xcrun simctl list devices available | awk -v n="$DEVICE_NAME" '
  $0 ~ n { if (match($0, /\(([A-F0-9-]{36})\)/)) { print substr($0, RSTART+1, RLENGTH-2); exit } }
')
if [[ -z "${UDID:-}" ]]; then
  echo "Device not found: $DEVICE_NAME — create with: xcrun simctl create \"$DEVICE_NAME\" com.apple.CoreSimulator.SimDeviceType.iPhone-15 com.apple.CoreSimulator.SimRuntime.iOS-17-5"
  exit 1
fi

STATE=$(xcrun simctl list devices | grep "$UDID" | grep -o 'Booted\|Shutdown' | head -1 || true)
if [[ "$STATE" != "Booted" ]]; then
  xcrun simctl boot "$UDID" || true
  open -a Simulator --args -CurrentDeviceUDID "$UDID" || true
  sleep 4
fi

# Health check — fail loud, no fake data
if ! curl -fsS --max-time 3 "$API_BASE/api/wc/health" >/dev/null; then
  echo "REFUSED: wonder-server unreachable at $API_BASE — start with examples/wonder-campus/scripts/run-dev-server.sh"
  exit 2
fi

xcrun simctl uninstall "$UDID" "$BUNDLE_ID" >/dev/null 2>&1 || true
xcrun simctl install "$UDID" "$OUT_APP"

# simctl 通过调用方环境变量 SIMCTL_CHILD_* 注入子进程环境（非 --setenv）
export SIMCTL_CHILD_WC_API_BASE="$API_BASE"
export SIMCTL_CHILD_WC_ENV="${WC_ENV:-dev}"
export SIMCTL_CHILD_WC_ALLOW_DEMO_LOGIN=1
export SIMCTL_CHILD_WC_LOAD_REMOTE=1
if [[ -n "${WC_ACCEPT_LOGIN:-}" ]]; then
  export SIMCTL_CHILD_WC_ACCEPT_LOGIN="$WC_ACCEPT_LOGIN"
  export SIMCTL_CHILD_WC_ACCEPT_IDENTITY="${WC_ACCEPT_IDENTITY:-ADMIN}"
fi
xcrun simctl launch --terminate-running-process "$UDID" "$BUNDLE_ID"
sleep 5
SHOT="$SHOT_DIR/${DEVICE_NAME// /-}-$(date +%H%M%S).png"
xcrun simctl io "$UDID" screenshot "$SHOT"
echo "Screenshot: $SHOT"
echo "UDID=$UDID BUNDLE=$BUNDLE_ID API=$API_BASE"
