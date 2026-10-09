#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/scripts/sync_mobile_assets.sh"
IOS="$ROOT/mobile/ios"
cd "$IOS"

# Detect project name (Calculator template or Sudoku)
PROJ=""
for c in Sudoku.xcodeproj Calculator.xcodeproj; do
  [[ -d "$c" ]] && PROJ="$c" && break
done
SCHEME="Calculator"
[[ -d Sudoku.xcodeproj ]] && SCHEME="Sudoku"

CANDIDATES=()
[[ -n "${IOS_DESTINATION:-}" ]] && CANDIDATES+=("$IOS_DESTINATION")
CANDIDATES+=(
  "platform=iOS Simulator,name=iPhone 15"
  "platform=iOS Simulator,name=iPhone 15 Pro"
  "generic/platform=iOS Simulator"
)

# Prefer already-booted simulator by UDID when name+OS.latest fails
# (Xcode 26.x SDK may require a matching iOS 26 runtime that is not installed).
while IFS= read -r line; do
  udid=$(echo "$line" | sed -n 's/.*(\([A-F0-9-]\{36\}\)).*/\1/p')
  [[ -n "$udid" ]] && CANDIDATES+=("platform=iOS Simulator,id=$udid")
done < <(xcrun simctl list devices available 2>/dev/null | rg -i "iphone" || true)

ok=0
for DEST in "${CANDIDATES[@]}"; do
  echo "==> xcodebuild destination: $DEST"
  if xcodebuild -project "$PROJ" -scheme "$SCHEME" \
      -destination "$DEST" -configuration Debug \
      CODE_SIGNING_ALLOWED=NO build; then
    ok=1
    echo "iOS build OK ($DEST)"
    break
  fi
done
if [[ "$ok" != 1 ]]; then
  SDKVER=$(xcrun --sdk iphonesimulator --show-sdk-version 2>/dev/null || echo "?")
  RUNTIMES=$(xcrun simctl list runtimes 2>/dev/null | tr '\n' ' ' || true)
  echo "BLOCKED: no usable iOS Simulator destination for scheme $SCHEME" >&2
  echo "  iphonesimulator SDK=$SDKVER" >&2
  echo "  installed runtimes: $RUNTIMES" >&2
  echo "  Hint: Xcode Settings → Components → download matching iOS Simulator runtime." >&2
  echo "  Mobile www assets were still synced; touch shell can be verified in a browser." >&2
  exit 2
fi
