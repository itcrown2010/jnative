#!/usr/bin/env bash
# Author: 张晓达
# Uninstall Finder Sync appex registration, legacy Automator workflows, and app.
# Never deletes user archives. Settings/inbox cleared only with JN_ARCHIVE_PURGE_USER_DATA=1.
set -euo pipefail

SERVICES="${HOME}/Library/Services"
DEST_APP="${HOME}/Applications/JNativeArchive.app"
ALT_APP="/Applications/JNativeArchive.app"
SUPPORT="${HOME}/Library/Application Support/JNativeArchive"
APPEX_ID="io.jnative.examples.archive.findersync"

echo "将删除:"
echo "  - Finder Sync 扩展注册（$APPEX_ID）"
echo "  - ${SERVICES}/JNative Archive*.workflow（仅本项目旧菜单，若仍存在）"
echo "  - ${DEST_APP}（若存在）"
if [[ -d "$ALT_APP" ]]; then
  echo "  - ${ALT_APP}（若存在且为同标识安装）"
fi
if [[ "${JN_ARCHIVE_PURGE_USER_DATA:-}" == "1" ]]; then
  echo "  - ${SUPPORT}（用户明确要求清除设置/inbox）"
else
  echo "保留: 用户压缩包、以及 ${SUPPORT}（设置/inbox）。清除请设 JN_ARCHIVE_PURGE_USER_DATA=1"
fi

if [[ "${JN_ARCHIVE_INSTALL_YES:-}" != "1" ]]; then
  read -r -p "继续卸载? [y/N] " ans
  case "$ans" in
    y|Y|yes|YES) ;;
    *) echo "已取消"; exit 0 ;;
  esac
fi

# Disable / forget Finder Sync
pluginkit -e ignore -i "$APPEX_ID" 2>/dev/null || true
if [[ -d "$DEST_APP/Contents/PlugIns/JNativeFinderSync.appex" ]]; then
  pluginkit -r "$DEST_APP/Contents/PlugIns/JNativeFinderSync.appex" 2>/dev/null || true
fi

removed=0
while IFS= read -r -d '' wf; do
  echo "Removing $wf"
  rm -rf "$wf"
  removed=$((removed + 1))
done < <(find "$SERVICES" -maxdepth 1 -type d -name 'JNative Archive*.workflow' -print0 2>/dev/null)

if [[ -d "$DEST_APP" ]]; then
  echo "Removing $DEST_APP"
  rm -rf "$DEST_APP"
fi
if [[ -d "$ALT_APP" ]]; then
  bid="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$ALT_APP/Contents/Info.plist" 2>/dev/null || true)"
  if [[ "$bid" == "io.jnative.examples.archive" ]]; then
    echo "Removing $ALT_APP"
    rm -rf "$ALT_APP"
  fi
fi

if [[ "${JN_ARCHIVE_PURGE_USER_DATA:-}" == "1" && -d "$SUPPORT" ]]; then
  echo "Purging $SUPPORT"
  rm -rf "$SUPPORT"
fi

/System/Library/CoreServices/pbs -flush 2>/dev/null || true
echo "Removed $removed workflow(s). Uninstall done."
echo "右键「JNative」应消失；若仍缓存，注销或重启 Finder。"
