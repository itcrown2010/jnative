# VS Code 统一入口 — 支持矩阵（0.8.1）

| 能力 | 桌面 | Web | Android | iOS |
|------|------|-----|---------|-----|
| 创建模板 | IMPLEMENTED | IMPLEMENTED | IMPLEMENTED（CLI mobile） | IMPLEMENTED（CLI mobile） |
| 构建 | VERIFIED | VERIFIED（wasm32-browser） | VERIFIED（guide-tasks APK，本机） | VERIFIED（guide-tasks 模拟器，本机） |
| 运行 | VERIFIED | VERIFIED（预览） | IMPLEMENTED（需设备；本机无 adb **未验证安装**） | VERIFIED（simctl 安装启动） |
| 设备选择 | — | — | QuickPick（多设备必选） | QuickPick（多设备必选） |
| 停止本次会话 | VERIFIED（进程） | VERIFIED（预览） | IMPLEMENTED（`mobile stop`） | IMPLEMENTED（`mobile stop`） |
| 分层错误日志 | IMPLEMENTED | VERIFIED（diag + 编译层） | IMPLEMENTED | VERIFIED（运行路径） |
| 源码调试 | IMPLEMENTED（CodeLLDB） | **未支持**（明确拒绝） | **未支持** LLDB/WASM | **未支持** LLDB/WASM |
| 打包 | IMPLEMENTED | IMPLEMENTED（dist） | IMPLEMENTED（release 路径；未签名） | IMPLEMENTED（模拟器；未签名） |
| 完整原生移动 UI | **未支持** | — | **未支持**（WebView 容器） | **未支持**（WebView 容器） |

业务示例：`examples/guide-tasks`（任务管理）。验收细节见 `docs/developer/VSCODE_MOBILE_ACCEPTANCE.md`。
