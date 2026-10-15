#!/usr/bin/env bash
# Author: 张晓达 — pack JNativeArchive.app with document types (Open With).
# Finder right-click menus are installed separately via Quick Actions (Services folder).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
OUT="${OUT_DIR:-$ROOT/build}"
BIN="$OUT/jn-archive-gui"
NAME="JNativeArchive"
ID="io.jnative.examples.archive"

if [[ ! -x "$BIN" ]]; then
  echo "missing $BIN — run build-gui.sh first" >&2
  exit 1
fi

"$REPO/scripts/macos/pack-app.sh" \
  --bin "$BIN" \
  --name "$NAME" \
  --id "$ID" \
  --out "$OUT"

APP="$OUT/${NAME}.app"
PLIST="$APP/Contents/Info.plist"

# Merge document type so Finder can Open With → JNativeArchive (does NOT steal default ZIP handler).
python3 - <<'PY' "$PLIST"
import plistlib, sys
path = sys.argv[1]
with open(path, "rb") as f:
    pl = plistlib.load(f)
pl["CFBundleDocumentTypes"] = [{
    "CFBundleTypeName": "ZIP Archive",
    "CFBundleTypeRole": "Viewer",
    "LSHandlerRank": "Alternate",
    "LSItemContentTypes": ["public.zip-archive", "com.pkware.zip-archive"],
    "CFBundleTypeExtensions": ["zip"],
}]
pl["NSSupportsSuddenTermination"] = False
# Do not register NSServices — Finder entry is Finder Sync 「JNative」 submenu.
# Optional Automator Quick Actions remain available via JN_ARCHIVE_INSTALL_AUTOMATOR=1.
if "NSServices" in pl:
    del pl["NSServices"]
with open(path, "wb") as f:
    plistlib.dump(pl, f)
print("Info.plist document types updated (no NSServices stub)")
PY

# Bundle launcher inside the app so install does not depend on the build tree.
mkdir -p "$APP/Contents/Resources/shell"
cp -f "$ROOT/integration/macos/jn-archive-launch.sh" "$APP/Contents/Resources/shell/jn-archive-launch.sh"
chmod +x "$APP/Contents/Resources/shell/jn-archive-launch.sh"

# Embed Finder Sync appex (native 「JNative」 context menu).
chmod +x "$ROOT/integration/macos/FinderSync/build-appex.sh"
"$ROOT/integration/macos/FinderSync/build-appex.sh" "$APP"

# Record distribution metadata.
cat >"$APP/Contents/Resources/DISTRIBUTION.txt" <<EOF
JNative Archive
Bundle: $ID
Minimum macOS: 11.0
Engine: jn_zip statically linked; system libz/libiconv/Cocoa only
Finder menu: Finder Sync appex (PlugIns/JNativeFinderSync.appex) — 右键 → JNative
Legacy Automator Quick Actions: optional (JN_ARCHIVE_INSTALL_AUTOMATOR=1)
Install: integration/macos/install.sh → ~/Applications + enable Finder extension
EOF

echo "OK $APP"
echo "Install: integration/macos/install.sh"
echo "Menus: Finder 右键 → JNative → …（需启用 Finder 扩展）"
