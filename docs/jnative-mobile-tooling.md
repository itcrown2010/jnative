# JNative 移动开发工具

作者：张晓达

将 Mobile MVP 整理为可复用模板与统一 CLI。

## 前置状态（验收分类）

| 能力 | 状态 |
|------|------|
| WASM 执行用户源码（非固定模板） | **已实现 + 已验证**（`MobileCalculatorWasmTest` / `verify_web.sh`） |
| 共享计算器从源码重建 | **已实现 + 已验证** |
| 桥接请求关联 / 参数校验 / 错误 / 超时 | **已实现 + 已验证**（Node harness） |
| 桥接 handshake（版本 / 平台 / 能力） | **已实现 + 已验证**（`MobileBridgeHandshakeTest`） |
| Android / iOS 加载本地打包资源 | **已实现** |
| 模板 → `mobile build --target web` | **已验证**（`/tmp/.../DemoMobile`） |
| 模板 → `mobile build --target android` | **已验证**（APK；`applicationId` 来自 toml） |
| 模板 → `mobile build --target ios` | **环境阻塞**（本机缺 iOS 26.5 Simulator runtime；`xcodebuild` 无可用 destination） |
| 设备安装运行 | Android 无在线 device **未验证**；iOS 开机 UI **未验证** |

工程生成或 `assembleDebug` 成功 **≠** 设备上已跑通 UI。

## `jnative.toml` `[mobile]`

```toml
[mobile]
app_name = "My App"
android_application_id = "io.example.myapp"
ios_bundle_id = "io.example.myapp"
version = "0.1.0"
build_number = "1"
entry = "src/App.java"
web_root = "web"
web_entry = "web/index.html"
platforms_root = "platforms"
targets = ["web", "android", "ios"]
capabilities = ["platform.getName", "storage.get", "storage.set", "storage.remove", "lifecycle"]
```

校验：非法 applicationId / bundle id、重复 target、未知能力、缺失 entry。首轮不自动授予系统权限。

## 模板

```text
jnative new MyApp --template mobile
```

布局：

```text
MyApp/
├── jnative.toml
├── src/App.java
├── assets/
├── web/
├── platforms/android/
├── platforms/ios/
└── README.md
```

生成器 **不覆盖** 已有文件。升级模板不会重写用户改过的原生代码。

## 命令

```bash
jnative doctor --mobile
jnative new demo --template mobile
cd demo
jnative mobile build --target web
jnative mobile build --target android
jnative mobile build --target ios
jnative mobile devices --target android
jnative mobile run --target android --device <id>   # 仅在用户显式 run 后安装启动
jnative mobile logs --target android --device <id>
```

规则：

- 进程参数结构化传递（支持空格 / 中文路径）。
- 构建失败立即停止，不安装旧产物（依赖 `.jnative/mobile-staging/*.ok` stamp）。
- 多设备时必须 `--device`，不猜测。
- 构建记录：`.jnative/mobile-build.json`（源码 / wasm / 平台产物哈希与标识符）。

## 桥接 handshake

1. 客户端 `bridge.init()` → `bridge.hello`
2. 主机返回 `{ version, platform, capabilities }`
3. 版本不匹配 → `PROTOCOL_MISMATCH`（停止后续业务请求）
4. 缺少所需能力 → `CAPABILITY_UNAVAILABLE`
5. 页面销毁 / 重载调用 `destroy()`，再 `init()` 可安全重建

## 分层排错

| 层 | 手段 |
|----|------|
| JNative 编译 | CLI 诊断行 |
| 页面 JS / Promise | `window.onerror` / `unhandledrejection`（app-shell） |
| WASM | 加载失败与 export 缺失提示 |
| 桥接 | `TIMEOUT` / `PROTOCOL_MISMATCH` / `CAPABILITY_UNAVAILABLE`（日志不含完整存储内容） |
| 平台 | `jnative mobile logs` |

**不宣称** LLDB 可自动调试 WebView 内 WASM；无 WASM 源码映射时只能定位到上述层级。

## VS Code

插件命令：创建移动项目、检查移动工具链、构建移动目标、运行移动应用。
