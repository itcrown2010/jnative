# 能力支持矩阵

状态：**稳定** | **实验性** | **未支持** | **未验证**  
证据：`IMPLEMENTED`（有实现）≠ `VERIFIED`（有自动化/文档验收）。声明支持不得跳过验收。  
环境快照：2026-10-09，macOS arm64（darwin），JNative JAR `0.32`。  
权威代码：`io.jnative.target.TargetProfiles`。

## 编译目标（勿混淆）

| 目标 | 编译 triple | 说明 |
|------|-------------|------|
| **native** | `native` | 主机原生二进制（clang/LLVM）。平台 = 当前 host OS/arch。 |
| **wasm32-browser** | `wasm32-browser` | 浏览器 WASM + JS glue。单线程；无主机 FS/原始套接字/原生 FFI。 |
| **mobile-container** | 实际编译为 **wasm32-browser** | iOS/Android WebView 壳嵌入 WASM。**不是**完整原生移动运行时（无原生控件、无 mobile-native FFI/THREADS）。 |

包 `requires` 与后端标志统一由 `TargetProfiles.packageFlags` 提供；`pkg.TargetCapability.forTriple("wasm32-browser")` **不再**错误声明 `THREADS`。

## 语言与运行时

| 功能 | native (macOS arm64) | wasm32-browser | mobile-container | 备注 |
|------|----------------------|----------------|------------------|------|
| 数值 | VERIFIED | VERIFIED | 同 wasm（未单独验） | |
| 对象 / 数组 | VERIFIED | VERIFIED | 同 wasm | |
| 动态调用 / 闭包 | IMPLEMENTED | IMPLEMENTED | 同 wasm | 未单独标 VERIFIED |
| 异常 / 资源管理 | IMPLEMENTED | IMPLEMENTED | 同 wasm | |
| GC | IMPLEMENTED | IMPLEMENTED | 同 wasm | WASM 单线程模型 |
| 线程 | IMPLEMENTED | **未支持** | **未支持** | |
| async/await | IMPLEMENTED | IMPLEMENTED | 同 wasm | 恢复失败见调试章 |

## I/O / UI / FFI

| 功能 | native | wasm32-browser | mobile-container |
|------|--------|----------------|------------------|
| 文件系统 | VERIFIED (mac arm64 smoke) | **未支持** | **未支持**（非完整原生） |
| 网络（原始套接字 / 服务端） | IMPLEMENTED | **未支持** | **未支持** |
| 数据库 | IMPLEMENTED | **未支持** | **未支持** / 未验证桥接 |
| TLS / HTTP2 原生 | IMPLEMENTED | **未支持** | **未支持** |
| 桌面 UI | IMPLEMENTED | **未支持** | **未支持** |
| 浏览器 DOM / JS | **未支持** | VERIFIED (guide-tasks 壳 + Chrome e2e) | IMPLEMENTED 壳；设备交互 **未验证** |
| WASM `storage_get` | — | IMPLEMENTED（需新 JAR） | 同 wasm |
| WASM `fetch_get` | — | **未支持**（JS 适配器 fetch） | 同 wasm |
| 原生 FFI | IMPLEMENTED | **未支持** | **未支持** |
| 原生移动控件 UI | **未支持** | — | **未支持** |

## 工具与 IDE

| 功能 | 状态 | 平台备注 |
|------|------|----------|
| `jnative doctor`（桌面工具链） | 稳定 | macOS arm64 已验证 |
| `jnative doctor --mobile` | 未验证 | |
| 构建前能力检查（API / 包 requires） | 实验性 | 诊断码 `JN2101` / `JN2002` |
| 统一诊断 JSON（code/hint） | 实验性 | CLI / Problems 同源 |
| VS Code 目标切换 + 状态栏 | 实验性 | 扩展 0.7.0；编辑时不做完整原生构建 |
| VS Code + CodeLLDB | 实验性 | 失败禁跑旧产物 |

## 验证对应关系

| 能力项 | 证据测试 / 示例 | 状态 |
|--------|-----------------|------|
| wasm 无 THREADS | `TargetCapabilityUnifiedTest` / `WasmBrowserTargetTest` | VERIFIED |
| wasm 拒 Files | `TargetCapabilityUnifiedTest` | VERIFIED |
| native 允许 Files | `TargetCapabilityUnifiedTest` | VERIFIED |
| mobile ≠ native runtime | `TargetProfiles` + 本文 | 声明已对齐；设备交互未验证 |
| 多目标独立输出 | `CompilerSession` per-target staging | IMPLEMENTED |
| 失败不启动旧产物 | VS Code runner + `build-result.json` status | IMPLEMENTED；端到端部分未验证 |
| 字节级可复现 | — | **未承诺**（需固定工具链；行为/输入指纹优先） |

详细阶段验收见 `STAGES.md` / `FACT_INVENTORY.md`。
