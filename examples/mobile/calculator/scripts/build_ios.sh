#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IOS="$ROOT/ios"

"$ROOT/scripts/build_web.sh"
"$ROOT/scripts/sync_mobile_assets.sh"

if ! command -v xcodebuild >/dev/null; then
  echo "BLOCKED: xcodebuild not found." >&2
  exit 2
fi

cd "$IOS"

# Prefer an explicit destination; fall back to a few common ones.
CANDIDATES=()
if [[ -n "${IOS_DESTINATION:-}" ]]; then
  CANDIDATES+=("$IOS_DESTINATION")
fi
CANDIDATES+=(
  "generic/platform=iOS Simulator"
  "platform=iOS Simulator,name=iPhone 15"
  "platform=iOS Simulator,name=iPhone 16"
)

ok=0
for DEST in "${CANDIDATES[@]}"; do
  echo "==> trying destination: $DEST"
  if xcodebuild -project Calculator.xcodeproj -scheme Calculator \
      -destination "$DEST" \
      -configuration Debug \
      CODE_SIGNING_ALLOWED=NO \
      build; then
    echo "iOS build finished (destination: $DEST)"
    ok=1
    break
  fi
done

if [[ "$ok" -ne 1 ]]; then
  echo "BLOCKED: xcodebuild could not find a usable iOS Simulator destination." >&2
  echo "Xcode SDK may be present while the matching Simulator runtime is missing." >&2
  echo "Install the iOS Simulator runtime from Xcode → Settings → Components," >&2
  echo "then re-run. Swift sources typecheck separately with swiftc -typecheck." >&2
  # Partial verification: typecheck against iphonesimulator SDK
  SDK="$(xcrun --sdk iphonesimulator --show-sdk-path 2>/dev/null || true)"
  if [[ -n "$SDK" ]]; then
    swiftc -typecheck -sdk "$SDK" -target arm64-apple-ios16.0-simulator \
      Calculator/AppDelegate.swift Calculator/ViewController.swift Calculator/BridgeHost.swift \
      && echo "PARTIAL: Swift typecheck OK against $SDK"
  fi
  exit 2
fi
