# 移动 / Web 平台结构（事实说明）

作者：张晓达

本文描述当前仓库的真实分层，不把「容器能跑」写成「完整原生移动运行时」。

## 分层

| 层 | 内容 | 典型路径 |
|----|------|----------|
| JNative 共享业务 | `.java` / `.jn` 源码（TaskCore、SyncCore、计算器等） | `examples/guide-tasks/core/`、`examples/mobile/calculator/src/` |
| WASM 逻辑 | `wasm32-browser` 编译产物 `app.wasm` + 运行时胶水 `app.js` | `dist/` 或 `web/dist/` |
| JavaScript 适配 | DOM、路由、IndexedDB、同步队列、桥接客户端 | `web/app-shell.js`、`web/lib/*`、`web/bridge.js` |
| Android 外壳 | Kotlin WebView + `BridgeHost` + assets/www | `platforms/android` 或 `mobile/android` |
| iOS 外壳 | Swift WKWebView + `BridgeHost` + www | `platforms/ios` 或 `mobile/ios` |
| 平台桥接 | `bridge.hello` / storage / lifecycle；有超时与能力协商 | 原生 `BridgeHost` ↔ JS |

## 产物关系

```text
JNative 源码 ──compile──► app.wasm + app.js
web 壳资源 ──overlay──► dist/（同一次成功构建）
dist/ ──sync──► Android assets/www 与 iOS Bundle www
原生工程 ──gradle/xcode──► APK / .app（调试或未签名发布配置）
```

规则：

- 构建失败不安装旧产物（依赖 `.jnative/mobile-staging/*.ok` stamp）。
- JS 与 WASM 必须来自同一次成功构建（见 `asset-manifest.json` / `mobile-build.json` 哈希）。
- **原生 LLDB 调试不会自动覆盖 WASM**；CodeLLDB 仅用于桌面原生可执行文件。

## 编译 / 运行 / 调试入口

| 动作 | CLI | VS Code |
|------|-----|---------|
| 检查工具链 | `jnative doctor --mobile` | JNative: 检查移动工具链 |
| 构建 | `jnative mobile build --target web\|android\|ios [--configuration debug\|release]` | JNative: 构建移动目标 / 运行 |
| 设备列表 | `jnative mobile devices --target … [--format json]` | 运行时 QuickPick |
| 安装启动 | `jnative mobile run --target … --device ID` | JNative: 运行（先构建再安装） |
| 停止 | `jnative mobile stop --target … --device ID` | JNative: 停止 |
| 日志 | `jnative mobile logs …` | 输出面板 + 容器日志命令 |

## guide-tasks 特例

任务示例通过 `web_build_script = "scripts/cli_web_build.sh"` 合并 TaskCore+SyncCore，再写入项目根 `dist/`，以便统一 CLI / VS Code 路径。业务源码仍在 `core/`，触屏壳在 `mobile/web/`。
