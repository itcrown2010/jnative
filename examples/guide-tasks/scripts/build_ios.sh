#!/usr/bin/env bash
# Author: 张晓达
# Simulator build (CODE_SIGNING_ALLOWED=NO). Device signing is a separate step.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/scripts/sync_mobile_assets.sh"
IOS="$ROOT/mobile/ios"
cd "$IOS"
PROJ="Calculator.xcodeproj"
SCHEME="Calculator"

CANDIDATES=()
[[ -n "${IOS_DESTINATION:-}" ]] && CANDIDATES+=("$IOS_DESTINATION")
CANDIDATES+=(
  "platform=iOS Simulator,name=iPhone 15"
  "platform=iOS Simulator,name=iPhone 17"
  "generic/platform=iOS Simulator"
)

ok=0
for DEST in "${CANDIDATES[@]}"; do
  echo "==> xcodebuild destination: $DEST"
  if xcodebuild -project "$PROJ" -scheme "$SCHEME" \
      -destination "$DEST" -configuration Debug \
      CODE_SIGNING_ALLOWED=NO build; then
    ok=1
    echo "iOS simulator build OK ($DEST)"
    break
  fi
done
[[ "$ok" == "1" ]] || { echo "iOS simulator build FAILED"; exit 1; }
