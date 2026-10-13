# guide-tasks · 移动（容器方案）

作者：张晓达

## 定位

- **不是**原生 UIKit / Jetpack Compose 控件 UI。
- **是**：与数独相同的 **WKWebView / Android WebView** 容器，加载本地 `www/` 中的 WASM `TaskCore` + 触屏壳。

## 目录

| 路径 | 说明 |
|------|------|
| `web/` | 触屏壳源（`body.touch`、安全区、软键盘启发式） |
| `staging/*-www/` | `sync_mobile_assets.sh` 产物，可拷入容器 |
| `android/` / `ios/` | 可选完整容器工程（若存在则同步脚本会写入 www） |

## 命令

```bash
# 生成 staging www + 复验 WASM
./examples/guide-tasks/scripts/verify_mobile_assets.sh

# 浏览器仿真触屏壳
./examples/guide-tasks/scripts/sync_mobile_assets.sh
cd examples/guide-tasks/mobile/staging/android-www
python3 -m http.server 8768
```

统一 CLI（推荐）：

```bash
java -jar target/jnative.jar mobile build --target android --project examples/guide-tasks
java -jar target/jnative.jar mobile build --target ios --project examples/guide-tasks
java -jar target/jnative.jar mobile run --target ios --device <UDID> --project examples/guide-tasks
```

调试构建启用 WebView 检查；发布配置关闭。商店签名 / 上传不在本仓库流程内。  
详见 `docs/developer/VSCODE_MOBILE_RUN.md`。
