#!/usr/bin/env bash
# Author: 张晓达
# Install JNativeArchive.app to ~/Applications, embed/enable Finder Sync,
# and remove legacy Automator Quick Actions (unless JN_ARCHIVE_INSTALL_AUTOMATOR=1).
#
# Distribution: development-signed locally when identity available; not notarized.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC_APP="${JNATIVE_ARCHIVE_SRC_APP:-$ROOT/build/JNativeArchive.app}"
DEST_APP="${HOME}/Applications/JNativeArchive.app"
SERVICES="${HOME}/Library/Services"
MARKER="io.jnative.examples.archive"
APPEX_ID="io.jnative.examples.archive.findersync"

if [[ ! -x "$SRC_APP/Contents/MacOS/JNativeArchive" ]]; then
  echo "Missing app: $SRC_APP" >&2
  echo "Build first: examples/jnative-archive/scripts/build-gui.sh" >&2
  exit 1
fi

echo "即将安装:"
echo "  App → $DEST_APP"
echo "  Finder Sync → PlugIns/JNativeFinderSync.appex（右键 → JNative）"
if [[ "${JN_ARCHIVE_INSTALL_AUTOMATOR:-}" == "1" ]]; then
  echo "  兼容: 同时安装旧 Automator Quick Actions"
else
  echo "  将移除本项目旧 Automator 服务（JNative Archive*.workflow）"
fi
echo "  不会修改系统默认 ZIP 关联 / 不会删除其他应用的服务项"
if [[ "${JN_ARCHIVE_INSTALL_YES:-}" != "1" ]]; then
  read -r -p "继续安装? [y/N] " ans
  case "$ans" in
    y|Y|yes|YES) ;;
    *) echo "已取消"; exit 0 ;;
  esac
fi

mkdir -p "${HOME}/Applications"
# Appex may be loaded — unload so ditto can replace PlugIns.
pluginkit -e ignore -i "$APPEX_ID" 2>/dev/null || true
killall JNativeFinderSync 2>/dev/null || true
killall JNativeArchive 2>/dev/null || true
sleep 0.4
rm -rf "$DEST_APP"
ditto "$SRC_APP" "$DEST_APP"
chmod +x "$DEST_APP/Contents/MacOS/JNativeArchive"

# Always rebuild/embed Finder Sync so upgrades pick up menu changes.
echo "Embedding Finder Sync appex into installed app…"
"$ROOT/integration/macos/FinderSync/build-appex.sh" "$DEST_APP"

LAUNCH="$DEST_APP/Contents/Resources/shell/jn-archive-launch.sh"
if [[ ! -x "$LAUNCH" ]]; then
  mkdir -p "$(dirname "$LAUNCH")"
  cp -f "$ROOT/integration/macos/jn-archive-launch.sh" "$LAUNCH"
  chmod +x "$LAUNCH"
fi

# Remove legacy Automator workflows belonging to this project only.
removed=0
if [[ "${JN_ARCHIVE_INSTALL_AUTOMATOR:-}" != "1" ]]; then
  while IFS= read -r -d '' wf; do
    echo "Removing legacy $wf"
    rm -rf "$wf"
    removed=$((removed + 1))
  done < <(find "$SERVICES" -maxdepth 1 -type d -name 'JNative Archive*.workflow' -print0 2>/dev/null)
  /System/Library/CoreServices/pbs -flush 2>/dev/null || true
else
  export JNATIVE_ARCHIVE_APP="$DEST_APP"
  export JN_ARCHIVE_INSTALL_YES=1
  export JN_ARCHIVE_LAUNCH="$LAUNCH"
  "$ROOT/integration/macos/install-finder-actions.sh"
fi

# Register with Launch Services.
/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister \
  -f "$DEST_APP" 2>/dev/null || true

# Register / enable Finder Sync appex (user may still need System Settings toggle once).
APPEX="$DEST_APP/Contents/PlugIns/JNativeFinderSync.appex"
if [[ -d "$APPEX" ]]; then
  pluginkit -a "$APPEX" 2>/dev/null || true
  pluginkit -e use -i "$APPEX_ID" 2>/dev/null || true
  echo "Finder Sync registered: $APPEX_ID"
  echo "若右键无「JNative」: 系统设置 → 通用 → 登录项与扩展 → 文件提供程序 → 启用 JNative"
  # Bounce extension so new binary/directoryURLs take effect.
  pluginkit -e ignore -i "$APPEX_ID" 2>/dev/null || true
  killall JNativeFinderSync 2>/dev/null || true
  sleep 0.5
  pluginkit -e use -i "$APPEX_ID" 2>/dev/null || true
fi

# One-shot user guidance file (app may show once). Never overwrite if present.
mkdir -p "${HOME}/Library/Application Support/JNativeArchive"
GUIDE="${HOME}/Library/Application Support/JNativeArchive/finder-extension-guide.txt"
if [[ ! -f "$GUIDE" ]]; then
  cat >"$GUIDE" <<EOF
JNative Finder 扩展启用说明
==========================
1. 打开「系统设置」→「通用」→「登录项与扩展」
2. 打开「文件提供程序」(File Providers) 详情
3. 勾选「JNative」
4. 在 Finder 中右键文件 → 第一层应出现「JNative」→ 子菜单压缩/解压

也可用: pluginkit -e use -i io.jnative.examples.archive.findersync

目录范围（directoryURLs）:
- 桌面 / 文稿 / 下载 / 影片 / 音乐 / 图片 / Public
- \$HOME 下用户自建目录（不含 \$HOME 本身与 Library）
- 已挂载的非启动卷（外接磁盘等）
不覆盖: 启动盘根目录整盘、iCloud Desktop/Documents 由系统/其他扩展接管的位置
（若桌面/文稿开启 iCloud 同步，菜单可能不出现；请在本地目录验证）。
EOF
fi

echo ""
echo "安装完成。"
echo "  App: $DEST_APP"
echo "  启动: open -a JNativeArchive"
echo "  菜单: Finder 选中文件 → 右键 → JNative → …"
echo "  已移除旧 Automator 工作流: $removed"
echo "  卸载: $ROOT/integration/macos/uninstall.sh"
echo "MARKER=$MARKER"
echo "APPEX_ID=$APPEX_ID"
