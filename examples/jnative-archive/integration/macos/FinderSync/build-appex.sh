#!/usr/bin/env bash
# Author: 张晓达 — build JNativeFinderSync.appex and embed into JNativeArchive.app
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
SRC="$(cd "$(dirname "$0")" && pwd)"
OUT="${OUT_DIR:-$ROOT/build}"
APP="${1:-$OUT/JNativeArchive.app}"
APPEX_NAME="JNativeFinderSync"
APPEX_ID="io.jnative.examples.archive.findersync"
HOST_ID="io.jnative.examples.archive"
MIN_OS="${JN_ARCHIVE_MIN_OS:-11.0}"

IDENTITY="${JN_ARCHIVE_CODESIGN_IDENTITY:-}"
if [[ -z "$IDENTITY" ]]; then
  IDENTITY="$(security find-identity -v -p codesigning 2>/dev/null \
    | awk -F'"' '/Apple Development|Developer ID Application|Mac Developer/{print $2; exit}')"
fi

if [[ ! -d "$APP/Contents/MacOS" ]]; then
  echo "missing app bundle: $APP — pack JNativeArchive.app first" >&2
  exit 1
fi

BUILD="$OUT/findersync-build"
APPEX="$BUILD/${APPEX_NAME}.appex"
rm -rf "$BUILD"
mkdir -p "$APPEX/Contents/MacOS" "$APPEX/Contents/Resources"

echo "==> compile Finder Sync ($APPEX_NAME)"
clang -fobjc-arc -fmodules -fapplication-extension \
  -mmacosx-version-min="$MIN_OS" \
  -framework Foundation -framework Cocoa -framework FinderSync \
  -o "$APPEX/Contents/MacOS/$APPEX_NAME" \
  "$SRC/main.m" "$SRC/JNativeFinderSync.m"

cp -f "$SRC/Info.plist" "$APPEX/Contents/Info.plist"
# Ensure executable bit
chmod +x "$APPEX/Contents/MacOS/$APPEX_NAME"

# Embed into host
PLUGINS="$APP/Contents/PlugIns"
rm -rf "$PLUGINS/${APPEX_NAME}.appex"
mkdir -p "$PLUGINS"
ditto "$APPEX" "$PLUGINS/${APPEX_NAME}.appex"

# Host Info.plist: declare plug-in + URL scheme (optional future)
/usr/libexec/PlistBuddy -c "Set :CFBundleDisplayName JNative Archive" "$APP/Contents/Info.plist" 2>/dev/null \
  || /usr/libexec/PlistBuddy -c "Add :CFBundleDisplayName string JNative Archive" "$APP/Contents/Info.plist" 2>/dev/null \
  || true

echo "==> codesign"
if [[ -z "$IDENTITY" ]]; then
  echo "WARNING: no codesigning identity — Finder Sync will not load until signed." >&2
  echo "Set JN_ARCHIVE_CODESIGN_IDENTITY or install an Apple Development cert." >&2
  echo "APPEX_UNSIGNED=$PLUGINS/${APPEX_NAME}.appex"
  exit 0
fi

ENT_APPEX="$SRC/JNativeFinderSync.entitlements"
ENT_APP="$SRC/JNativeArchive.entitlements"

# Sign appex first, then host.
# Development: omit hardened runtime so temporary-exception entitlements apply
# (needed for JN1 inbox). Notarized release builds can set JN_ARCHIVE_HARDENED=1.
SIGN_OPTS=(--force --sign "$IDENTITY" --timestamp=none)
if [[ "${JN_ARCHIVE_HARDENED:-}" == "1" ]]; then
  SIGN_OPTS+=(--options runtime)
fi

codesign "${SIGN_OPTS[@]}" --entitlements "$ENT_APPEX" \
  "$PLUGINS/${APPEX_NAME}.appex"

codesign "${SIGN_OPTS[@]}" --entitlements "$ENT_APP" \
  "$APP"

codesign --verify --verbose=2 "$PLUGINS/${APPEX_NAME}.appex" 2>&1 | tail -5
codesign --verify --verbose=2 "$APP" 2>&1 | tail -5

echo "OK embedded $PLUGINS/${APPEX_NAME}.appex"
echo "SIGN_IDENTITY=$IDENTITY"
echo "APPEX_ID=$APPEX_ID HOST_ID=$HOST_ID"
