#!/usr/bin/env bash
# Author: 张晓达 — remove only this project's Quick Actions / Services entries.
set -euo pipefail
SERVICES="${HOME}/Library/Services"

echo "将删除 ${SERVICES} 下名称以「JNative Archive」开头的 .workflow"
echo "不会删除用户压缩包，也不会删除其他应用的服务注册项。"
if [[ "${JN_ARCHIVE_INSTALL_YES:-}" != "1" ]]; then
  read -r -p "继续卸载? [y/N] " ans
  case "$ans" in
    y|Y|yes|YES) ;;
    *) echo "已取消"; exit 0 ;;
  esac
fi

removed=0
while IFS= read -r -d '' wf; do
  echo "Removing $wf"
  rm -rf "$wf"
  removed=$((removed + 1))
done < <(find "$SERVICES" -maxdepth 1 -type d -name 'JNative Archive*.workflow' -print0 2>/dev/null)

/System/Library/CoreServices/pbs -flush 2>/dev/null || true
echo "Removed $removed workflow(s)."
echo "App bundle (if any) is left in place — delete build/JNativeArchive.app manually if desired."
