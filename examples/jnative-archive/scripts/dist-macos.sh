#!/usr/bin/env bash
# Author: 张晓达 — reproducible macOS distribution folder (unsigned).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${OUT_DIR:-$ROOT/build}"
STAMP="$(date +%Y%m%d)"
DIST_NAME="JNativeArchive-macos-${STAMP}"
DIST="$OUT/dist/$DIST_NAME"
APP_SRC="$OUT/JNativeArchive.app"

if [[ ! -x "$APP_SRC/Contents/MacOS/JNativeArchive" ]]; then
  echo "missing $APP_SRC — run scripts/build-gui.sh" >&2
  exit 1
fi

rm -rf "$DIST"
mkdir -p "$DIST"
ditto "$APP_SRC" "$DIST/JNativeArchive.app"
cp -f "$ROOT/integration/macos/install.sh" "$DIST/install.sh"
cp -f "$ROOT/integration/macos/uninstall.sh" "$DIST/uninstall.sh"
cp -f "$ROOT/integration/macos/install-finder-actions.sh" "$DIST/install-finder-actions.sh"
cp -f "$ROOT/integration/macos/uninstall-finder-actions.sh" "$DIST/uninstall-finder-actions.sh"
cp -f "$ROOT/integration/macos/jn-archive-launch.sh" "$DIST/jn-archive-launch.sh"
chmod +x "$DIST"/*.sh "$DIST/JNativeArchive.app/Contents/MacOS/JNativeArchive"
chmod +x "$DIST/JNativeArchive.app/Contents/Resources/shell/jn-archive-launch.sh" 2>/dev/null || true

# Make install.sh work from the dist folder (app next to scripts).
cat >"$DIST/install-from-dist.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
export JNATIVE_ARCHIVE_SRC_APP="$HERE/JNativeArchive.app"
export JN_ARCHIVE_INSTALL_YES="${JN_ARCHIVE_INSTALL_YES:-0}"
# Minimal installer: copy app + register services using bundled helpers.
DEST="${HOME}/Applications/JNativeArchive.app"
mkdir -p "${HOME}/Applications"
rm -rf "$DEST"
ditto "$JNATIVE_ARCHIVE_SRC_APP" "$DEST"
mkdir -p "$DEST/Contents/Resources/shell"
cp -f "$HERE/jn-archive-launch.sh" "$DEST/Contents/Resources/shell/jn-archive-launch.sh"
chmod +x "$DEST/Contents/MacOS/JNativeArchive" "$DEST/Contents/Resources/shell/jn-archive-launch.sh"
export JNATIVE_ARCHIVE_APP="$DEST"
export JN_ARCHIVE_LAUNCH="$DEST/Contents/Resources/shell/jn-archive-launch.sh"
export JN_ARCHIVE_INSTALL_YES=1
# Inline workflow install by calling the copied script if present
if [[ -x "$HERE/install-finder-actions.sh" ]]; then
  # Patch ROOT expectation: script walks ../.. — run from a fake tree or duplicate logic.
  :
fi
SERVICES="${HOME}/Library/Services"
MARKER="io.jnative.examples.archive"
LAUNCH="$JN_ARCHIVE_LAUNCH"
mkdir -p "$SERVICES"
sq() { printf "%s" "$1" | sed "s/'/'\\\\''/g"; }
LAUNCH_Q="$(sq "$LAUNCH")"
gen_workflow() {
  local name="$1" action="$2" types="$3"
  local wf="$SERVICES/${name}.workflow"
  rm -rf "$wf"
  mkdir -p "$wf/Contents"
  cat >"$wf/Contents/document.wflow" <<WFEOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>actions</key><array><dict><key>action</key><dict>
    <key>ActionParameters</key><dict>
      <key>COMMAND_STRING</key>
      <string>LAUNCH='$LAUNCH_Q'
ACTION='$(sq "$action")'
if [[ \$# -gt 0 ]]; then
  printf '%s\\0' "\$@" | exec "\$LAUNCH" "\$ACTION"
else
  exec "\$LAUNCH" "\$ACTION"
fi
</string>
      <key>inputMethod</key><integer>1</integer>
      <key>shell</key><string>/bin/bash</string>
      <key>source</key><string></string>
      <key>CheckedForUserDefaultShell</key><true/>
    </dict>
    <key>ActionBundlePath</key><string>/System/Library/Automator/Run Shell Script.action</string>
  </dict></dict></array>
  <key>workflowMetaData</key><dict>
    <key>serviceInputTypeIdentifier</key><string>com.apple.Automator.${types}</string>
    <key>serviceOutputTypeIdentifier</key><string>com.apple.Automator.nothing</string>
    <key>serviceApplicationBundleID</key><string>com.apple.finder</string>
    <key>workflowTypeIdentifier</key><string>com.apple.Automator.servicesMenu</string>
  </dict>
</dict></plist>
WFEOF
  cat >"$wf/Contents/Info.plist" <<PLEOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>CFBundleIdentifier</key><string>${MARKER}.${action}</string>
  <key>CFBundleName</key><string>${name}</string>
  <key>NSServices</key><array><dict>
    <key>NSMenuItem</key><dict><key>default</key><string>${name}</string></dict>
    <key>NSMessage</key><string>runWorkflowAsService</string>
    <key>NSRequiredContext</key><dict>
      <key>NSApplicationIdentifier</key><string>com.apple.finder</string>
    </dict>
    <key>NSSendFileTypes</key><array><string>public.item</string></array>
  </dict></array>
</dict></plist>
PLEOF
  echo "Installed: $wf"
}
# Must match install-finder-actions.sh: fileSystemObject (not files-and-folders).
gen_workflow "JNative Archive Compress ZIP" "compress" "fileSystemObject"
gen_workflow "JNative Archive Compress ZIP Password" "compress-password" "fileSystemObject"
gen_workflow "JNative Archive Open ZIP" "open" "fileSystemObject"
gen_workflow "JNative Archive Extract Here" "extract-here" "fileSystemObject"
gen_workflow "JNative Archive Extract to Folder" "extract-sibling" "fileSystemObject"
gen_workflow "JNative Archive Extract To" "extract-to" "fileSystemObject"
/System/Library/CoreServices/pbs -flush 2>/dev/null || true
/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister -f "$DEST" 2>/dev/null || true
echo "OK installed $DEST"
echo "SIGNING: UNSIGNED (not notarized)"
EOF
chmod +x "$DIST/install-from-dist.sh"

cat >"$DIST/README.txt" <<EOF
JNative Archive — macOS 分发包
==============================

版本目录: ${DIST_NAME}
最低系统: macOS 11.0+
依赖: 系统 libz / libiconv / Cocoa（jn_zip 静态链接进主程序，不依赖开发目录）
签名/公证: 未签名、未公证 — 仅本地/侧载；Gatekeeper 可能提示「无法验证开发者」

安装（推荐）:
  JN_ARCHIVE_INSTALL_YES=1 ./install-from-dist.sh
  或将 JNativeArchive.app 拖到 ~/Applications 后，用仓库内 integration/macos/install.sh

启动:
  open -a JNativeArchive
  或 open ./JNativeArchive.app

菜单:
  Finder 选中文件/文件夹 → 右键 → 快速操作 → JNative Archive …

卸载:
  JN_ARCHIVE_INSTALL_YES=1 ./uninstall.sh
  默认保留 ~/Library/Application Support/JNativeArchive
  清除设置: JN_ARCHIVE_PURGE_USER_DATA=1 JN_ARCHIVE_INSTALL_YES=1 ./uninstall.sh

Windows 右键端到端: WINDOWS_E2E_UNVERIFIED（需真实 Windows 主机验收）
EOF

# Zip for transfer
(
  cd "$OUT/dist"
  rm -f "${DIST_NAME}.zip"
  ditto -c -k --sequesterRsrc --keepParent "$DIST_NAME" "${DIST_NAME}.zip"
)

echo "DIST_DIR=$DIST"
echo "DIST_ZIP=$OUT/dist/${DIST_NAME}.zip"
echo "SIGNING: UNSIGNED (not notarized)"
