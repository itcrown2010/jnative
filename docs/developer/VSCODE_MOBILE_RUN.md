# VS Code：运行、排错与打包移动应用

作者：张晓达

## 前置

1. 工作区受信任。
2. 配置 `jnative.jarPath` 或仓库内 `target/jnative.jar`。
3. `jnative doctor --mobile`（或命令「检查移动工具链」）按目标通过。

打开 `examples/guide-tasks` 或标准 `jnative.toml` `[mobile]` 项目。

## 运行流程

命令：**JNative: 运行**（或 **JNative: 运行移动应用**）

1. 选择目标：Web / Android / iOS。
2. 选择配置：debug（日志与检查）或 release（内置资源、关闭开发入口）。
3. Android/iOS：检查工具链 → 列出设备 → **多设备必须 QuickPick 明确选择**。
4. 构建（失败则不安装；会清除对应 stamp）。
5. 校验 `dist/app.wasm` + `app.js` 存在。
6. 安装并启动；输出面板带 `[requestId] [layer]`。
7. **JNative: 停止**：结束本扩展启动的预览，并 `mobile stop` 上次设备上的应用。

## 错误分层

| 层 | 含义 |
|----|------|
| toolchain | SDK / Xcode / adb / wat2wasm 等 |
| jnative-compile | 源码诊断（可点击时写入 Problems） |
| wasm | 产物验证 / 加载 |
| javascript | 壳层异常 |
| bridge | 协议 / 超时 / 能力 |
| install / launch | 安装或启动 |
| android-container / ios-container | Gradle / xcodebuild / 外壳 |

无源映射时只显示层级与原始信息，**不伪造** `.jn` 行号。

## 打包

**JNative: 打包** 对移动项目执行 `--configuration release` 构建，并打印产物清单与 `mobile-build.json`。

明确限制：`store_ready=false`；不上传商店；不创建或提交签名材料。

## WebView 检查（调试构建）

- Android：Chrome → `chrome://inspect` → 对应 WebView（`BuildConfig.DEBUG` 才启用）。
- iOS：Safari → 开发 → 模拟器（`#if DEBUG` + `isInspectable`）。
- 发布配置关闭上述开发入口。

详见 [MOBILE_PLATFORM_STRUCTURE.md](./MOBILE_PLATFORM_STRUCTURE.md) 与 [WASM_SOURCE_DEBUG.md](./WASM_SOURCE_DEBUG.md)。
