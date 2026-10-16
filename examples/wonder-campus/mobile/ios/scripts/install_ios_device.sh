#!/usr/bin/env bash
# Author: 张晓达
# 签名并安装奇想校园到已配对的物理 iPhone（需 Xcode 已登录 Apple ID）。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/scripts/sync_www.sh"

TEAM="${DEVELOPMENT_TEAM:-8F86W57524}"
BUNDLE_ID="${WC_BUNDLE_ID:-io.jnative.examples.wondercampus}"
LAN_IP="${WC_LAN_IP:-$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || true)}"
API_BASE="${WC_API_BASE:-}"
if [[ -z "$API_BASE" ]]; then
  if [[ -n "$LAN_IP" ]]; then
    API_BASE="http://${LAN_IP}:18880"
  else
    echo "BLOCKED: 无法获取局域网 IP，请设置 WC_API_BASE=http://<MacIP>:18880" >&2
    exit 2
  fi
fi

DEVICE_UDID="${IOS_DEVICE_UDID:-}"
if [[ -z "$DEVICE_UDID" ]]; then
  # 优先：xcodebuild 当前可见的物理机（跳过 Offline）
  DEVICE_UDID=$(xcrun xcodebuild -project "$ROOT/WonderCampus.xcodeproj" -scheme WonderCampus -showdestinations 2>/dev/null \
    | awk -F'[=,}]' '/platform:iOS,/ && /id:0000/ && $0 !~ /Simulator/ && $0 !~ /placeholder/ {
        for (i=1;i<=NF;i++) if ($i ~ /^0000/) { print $i; exit }
      }')
fi
if [[ -z "$DEVICE_UDID" ]]; then
  # xctrace：只取「== Devices ==」段，跳过 Offline
  DEVICE_UDID=$(xcrun xctrace list devices 2>/dev/null | awk '
    /^== Devices Offline/ { offline=1; next }
    /^== / { offline=0 }
    offline { next }
    /iPhone/ && $0 !~ /Simulator/ {
      if (match($0, /\(0000[0-9A-Fa-f-]+\)/)) {
        print substr($0, RSTART+1, RLENGTH-2); exit
      }
    }')
fi
if [[ -z "$DEVICE_UDID" ]]; then
  echo "BLOCKED: 未找到已连接/配对的 iPhone。请用数据线连接并信任此电脑。" >&2
  exit 2
fi


# 真机 Config / Info：临时改写后恢复，避免污染仓库默认（模拟器仍用 127.0.0.1）
CFG="$ROOT/WonderCampus/Config.dev.plist"
INFO="$ROOT/WonderCampus/Info.plist"
mkdir -p "$ROOT/build"
CFG_BAK="$ROOT/build/Config.dev.plist.devicebak"
INFO_BAK="$ROOT/build/Info.plist.devicebak"
cp "$CFG" "$CFG_BAK"
cp "$INFO" "$INFO_BAK"
cleanup() {
  cp "$CFG_BAK" "$CFG" 2>/dev/null || true
  cp "$INFO_BAK" "$INFO" 2>/dev/null || true
}
trap cleanup EXIT
/usr/libexec/PlistBuddy -c "Set :WC_API_BASE $API_BASE" "$CFG"
/usr/libexec/PlistBuddy -c "Set :WC_LOAD_REMOTE true" "$CFG" 2>/dev/null || true
/usr/libexec/PlistBuddy -c "Add :NSAppTransportSecurity:NSAllowsArbitraryLoads bool true" "$INFO" 2>/dev/null \
  || /usr/libexec/PlistBuddy -c "Set :NSAppTransportSecurity:NSAllowsArbitraryLoads true" "$INFO" 2>/dev/null || true


echo "==> device: $DEVICE_UDID"
echo "==> team:   $TEAM"
echo "==> api:    $API_BASE"
echo "==> bundle: $BUNDLE_ID"

DERIVED="$ROOT/build/DerivedData-device"
rm -rf "$DERIVED"
mkdir -p "$DERIVED"

cd "$ROOT"
xcodebuild -project WonderCampus.xcodeproj -scheme WonderCampus \
  -destination "platform=iOS,id=$DEVICE_UDID" \
  -configuration Debug \
  -derivedDataPath "$DERIVED" \
  -allowProvisioningUpdates \
  -allowProvisioningDeviceRegistration \
  DEVELOPMENT_TEAM="$TEAM" \
  CODE_SIGN_STYLE=Automatic \
  PRODUCT_BUNDLE_IDENTIFIER="$BUNDLE_ID" \
  build

APP=$(find "$DERIVED" -path '*/Debug-iphoneos/WonderCampus.app' -type d | head -1)
if [[ -z "$APP" || ! -d "$APP" ]]; then
  echo "BLOCKED: 未找到 Debug-iphoneos/WonderCampus.app" >&2
  exit 1
fi
echo "==> app: $APP"

xcrun devicectl device install app --device "$DEVICE_UDID" "$APP"
if xcrun devicectl device process launch --device "$DEVICE_UDID" \
  --environment-variables "{\"WC_API_BASE\":\"$API_BASE\",\"WC_LOAD_REMOTE\":\"1\",\"WC_ALLOW_DEMO_LOGIN\":\"1\"}" \
  "$BUNDLE_ID"; then
  echo "==> 已安装并启动「奇想校园」"
else
  echo "==> 已安装「奇想校园」，但未能自动启动（常见原因：尚未信任开发者证书）。"
  echo "    请在 iPhone：设置 → 通用 → VPN与设备管理 → 开发者 App → 信任"
  echo "    信任后再点桌面图标打开；或重新执行本脚本。"
fi
echo "    真机访问 API: $API_BASE"
echo "    登录: admin / Wonder2026 （需电脑上 wonder-server 在跑，且手机与 Mac 同一 Wi‑Fi）"
