#!/usr/bin/env bash
# Author: 张晓达
# CLI / VS Code 入口：产出项目根 dist/（与 jnative mobile build 约定一致）。
# 调试构建可带开发服务器提示；发布构建写入 configuration=release 的 build-info（由 MobileBuilder 覆盖）。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CFG="${JNATIVE_MOBILE_CONFIGURATION:-debug}"

"$ROOT/scripts/sync_mobile_assets.sh"

SRC="$ROOT/mobile/staging/android-www"
DIST="$ROOT/dist"
rm -rf "$DIST"
mkdir -p "$DIST"
cp -R "$SRC"/. "$DIST"/

# 发布构建：剥离开发期提示文件（若存在），并写入本地配置标记。
if [[ "$CFG" == "release" ]]; then
  rm -f "$DIST/dev-server.json" "$DIST/.dev-endpoint" 2>/dev/null || true
  # 确保不嵌入本机开发服务器地址
  if grep -RIl --include='*.js' --include='*.html' '127\.0\.0\.1:8787\|localhost:8787' "$DIST" >/dev/null 2>&1; then
    echo "warn: release dist still references localhost sync port — ensure runtime uses bundled offline path" >&2
  fi
fi

# 资源一致性戳：同一次构建的 wasm/js 哈希摘要（不含密钥）
WASM_SHA="$(shasum -a 256 "$DIST/app.wasm" | awk '{print $1}')"
JS_SHA="$(shasum -a 256 "$DIST/app.js" | awk '{print $1}')"
cat > "$DIST/asset-manifest.json" <<EOF
{
  "appName": "任务管理",
  "configuration": "$CFG",
  "wasmSha256": "$WASM_SHA",
  "jsSha256": "$JS_SHA",
  "builtAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF

echo "==> CLI web dist ready: $DIST (configuration=$CFG)"
ls -la "$DIST" | head -20
