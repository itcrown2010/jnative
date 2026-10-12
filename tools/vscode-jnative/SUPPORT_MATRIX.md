# VS Code 统一入口 — 支持矩阵（0.8.0）

| 能力 | 桌面 | Web | Android | iOS |
|------|------|-----|---------|-----|
| 创建模板（加法器） | IMPLEMENTED（扩展模板） | IMPLEMENTED（扩展模板） | IMPLEMENTED（CLI mobile） | IMPLEMENTED（CLI mobile） |
| 构建 | VERIFIED（本机 macOS） | VERIFIED（本机产出 wasm） | IMPLEMENTED（CLI） | IMPLEMENTED（CLI，需 Xcode） |
| 运行 | VERIFIED（原生可执行） | VERIFIED（构建成功；预览依赖 python3 + Simple Browser） | IMPLEMENTED（需设备 ID） | IMPLEMENTED（需模拟器 ID） |
| 调试 | IMPLEMENTED（CodeLLDB） | **未支持**（用 Console） | **未支持** LLDB | **未支持** LLDB |
| 打包说明 | IMPLEMENTED（说明产物） | IMPLEMENTED | IMPLEMENTED（无商店签名） | IMPLEMENTED（无商店签名） |
| 完整原生移动 UI | **未支持** | — | **未支持**（WebView 容器） | **未支持** |

业务主题：三端均为 **整数加法器**，计算在 JNative 中执行。
