#!/usr/bin/env bash
# Author: 张晓达
# Install Finder Quick Actions (Automator workflows → ~/Library/Services).
#
# Menu location (accurate):
#   Finder 右键 → 快速操作 (Quick Actions) → JNative Archive …
#   以及菜单栏 Finder → 服务 (Services) 子菜单（系统设置里可开关）。
# 这不是 Finder 顶层任意位置的全局菜单项。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SERVICES="${HOME}/Library/Services"
MARKER="io.jnative.examples.archive"

APP="${JNATIVE_ARCHIVE_APP:-}"
if [[ -z "$APP" ]]; then
  if [[ -x "${HOME}/Applications/JNativeArchive.app/Contents/MacOS/JNativeArchive" ]]; then
    APP="${HOME}/Applications/JNativeArchive.app"
  elif [[ -x "/Applications/JNativeArchive.app/Contents/MacOS/JNativeArchive" ]]; then
    APP="/Applications/JNativeArchive.app"
  else
    APP="$ROOT/build/JNativeArchive.app"
  fi
fi
LAUNCH="${JN_ARCHIVE_LAUNCH:-$APP/Contents/Resources/shell/jn-archive-launch.sh}"
if [[ ! -x "$LAUNCH" ]]; then
  LAUNCH="$ROOT/integration/macos/jn-archive-launch.sh"
fi

if [[ ! -d "$APP" ]]; then
  echo "Missing app: $APP — run scripts/build-gui.sh and integration/macos/install.sh" >&2
  exit 1
fi
if [[ ! -x "$LAUNCH" ]]; then
  chmod +x "$LAUNCH" 2>/dev/null || true
fi
if [[ ! -x "$LAUNCH" ]]; then
  echo "Missing launcher: $LAUNCH" >&2
  exit 1
fi

echo "即将安装 Finder Quick Actions 到: $SERVICES"
echo "  - 使用应用: $APP"
echo "  - 启动器: $LAUNCH"
echo "  - 仅添加名称为「JNative Archive *」的工作流；不会修改系统默认 ZIP 关联"
echo "  - 不会删除其他应用的服务项"
if [[ "${JN_ARCHIVE_INSTALL_YES:-}" != "1" ]]; then
  read -r -p "继续安装? [y/N] " ans
  case "$ans" in
    y|Y|yes|YES) ;;
    *) echo "已取消"; exit 0 ;;
  esac
fi

mkdir -p "$SERVICES"

# Escape for embedding in Automator COMMAND_STRING (single-quoted shell fragment).
sq() { printf "%s" "$1" | sed "s/'/'\\\\''/g"; }
LAUNCH_Q="$(sq "$LAUNCH")"

gen_workflow() {
  local name="$1" action="$2" types="$3"
  local wf="$SERVICES/${name}.workflow"
  rm -rf "$wf"
  mkdir -p "$wf/Contents"
  # Paths arrive as Automator arguments; forward via NUL stdin to avoid re-splitting
  # and to keep the launcher path out of a second eval of user filenames.
  cat >"$wf/Contents/document.wflow" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>AMApplicationBuild</key>
	<string>523</string>
	<key>AMApplicationVersion</key>
	<string>2.10</string>
	<key>AMDocumentVersion</key>
	<string>2</string>
	<key>actions</key>
	<array>
		<dict>
			<key>action</key>
			<dict>
				<key>AMAccepts</key>
				<dict>
					<key>Container</key>
					<string>List</string>
					<key>Optional</key>
					<false/>
					<key>Types</key>
					<array>
						<string>com.apple.cocoa.path</string>
					</array>
				</dict>
				<key>AMActionVersion</key>
				<string>2.0.3</string>
				<key>AMApplication</key>
				<array>
					<string>Automator</string>
				</array>
				<key>AMParameterProperties</key>
				<dict>
					<key>COMMAND_STRING</key>
					<dict/>
					<key>CheckedForUserDefaultShell</key>
					<dict/>
					<key>inputMethod</key>
					<dict/>
					<key>shell</key>
					<dict/>
					<key>source</key>
					<dict/>
				</dict>
				<key>AMProvides</key>
				<dict>
					<key>Container</key>
					<string>List</string>
					<key>Types</key>
					<array>
						<string>com.apple.cocoa.string</string>
					</array>
				</dict>
				<key>ActionBundlePath</key>
				<string>/System/Library/Automator/Run Shell Script.action</string>
				<key>ActionName</key>
				<string>Run Shell Script</string>
				<key>ActionParameters</key>
				<dict>
					<key>COMMAND_STRING</key>
					<string>LAUNCH='$LAUNCH_Q'
ACTION='$(sq "$action")'
# Forward selected paths as NUL-separated stdin (keeps path boundaries; no password).
if [[ \$# -gt 0 ]]; then
  printf '%s\\0' "\$@" | exec "\$LAUNCH" "\$ACTION"
else
  exec "\$LAUNCH" "\$ACTION"
fi
</string>
					<key>CheckedForUserDefaultShell</key>
					<true/>
					<key>inputMethod</key>
					<integer>1</integer>
					<key>shell</key>
					<string>/bin/bash</string>
					<key>source</key>
					<string></string>
				</dict>
				<key>BundleIdentifier</key>
				<string>com.apple.RunShellScript</string>
				<key>CFBundleVersion</key>
				<string>2.0.3</string>
				<key>CanShowSelectedItemsWhenRun</key>
				<false/>
				<key>CanShowWhenRun</key>
				<true/>
				<key>Category</key>
				<array>
					<string>AMCategoryUtilities</string>
				</array>
				<key>Class Name</key>
				<string>RunShellScriptAction</string>
				<key>InputUUID</key>
				<string>A1B2C3D4-0001-4000-8000-000000000001</string>
				<key>Keywords</key>
				<array>
					<string>Run</string>
					<string>Shell</string>
					<string>Script</string>
				</array>
				<key>OutputUUID</key>
				<string>A1B2C3D4-0001-4000-8000-000000000002</string>
				<key>UUID</key>
				<string>A1B2C3D4-0001-4000-8000-000000000003</string>
				<key>UnlocalizedApplications</key>
				<array>
					<string>Automator</string>
				</array>
			</dict>
		</dict>
	</array>
	<key>connectors</key>
	<dict/>
	<key>workflowMetaData</key>
	<dict>
		<key>serviceInputTypeIdentifier</key>
		<string>com.apple.Automator.${types}</string>
		<key>serviceOutputTypeIdentifier</key>
		<string>com.apple.Automator.nothing</string>
		<key>serviceApplicationBundleID</key>
		<string>com.apple.finder</string>
		<key>serviceApplicationPath</key>
		<string>/System/Library/CoreServices/Finder.app</string>
		<key>serviceProcessesInput</key>
		<integer>0</integer>
		<key>workflowTypeIdentifier</key>
		<string>com.apple.Automator.servicesMenu</string>
	</dict>
</dict>
</plist>
EOF
  cat >"$wf/Contents/Info.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>CFBundleIdentifier</key><string>${MARKER}.${action}</string>
  <key>CFBundleName</key><string>${name}</string>
  <key>CFBundleShortVersionString</key><string>1.0</string>
  <key>NSServices</key>
  <array>
    <dict>
      <key>NSMenuItem</key><dict><key>default</key><string>${name}</string></dict>
      <key>NSMessage</key><string>runWorkflowAsService</string>
      <key>NSRequiredContext</key>
      <dict>
        <key>NSApplicationIdentifier</key><string>com.apple.finder</string>
      </dict>
      <key>NSSendFileTypes</key>
      <array><string>public.item</string></array>
    </dict>
  </array>
</dict></plist>
EOF
  echo "Installed: $wf"
}

# serviceInputTypeIdentifier must be a real Automator type. Invalid values
# (e.g. files-and-folders / files) make Finder show:
#   「该服务无法运行，因为它配置不正确。」
# WinZip and system Services use fileSystemObject.
gen_workflow "JNative Archive Compress ZIP" "compress" "fileSystemObject"
gen_workflow "JNative Archive Compress ZIP Password" "compress-password" "fileSystemObject"
gen_workflow "JNative Archive Open ZIP" "open" "fileSystemObject"
gen_workflow "JNative Archive Extract Here" "extract-here" "fileSystemObject"
gen_workflow "JNative Archive Extract to Folder" "extract-sibling" "fileSystemObject"
gen_workflow "JNative Archive Extract To" "extract-to" "fileSystemObject"

/System/Library/CoreServices/pbs -flush 2>/dev/null || true
killall Finder 2>/dev/null || true

echo ""
echo "安装完成。"
echo "菜单入口: Finder 中选中文件/文件夹 → 右键 → 快速操作 → JNative Archive …"
echo "若看不到: 系统设置 → 隐私与安全性 → 扩展 → Finder / 或 键盘 → 快捷键 → 服务 中启用。"
echo "卸载: $ROOT/integration/macos/uninstall.sh"
echo "MARKER=$MARKER"
