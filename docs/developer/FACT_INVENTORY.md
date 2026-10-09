# JNative 开发者文档 · 事实清单

> 维护来源：以当前仓库实现与本机执行证据为准。文档站点渲染副本见 `website/reference/fact-inventory.md`。  
> 能力状态枚举：**稳定** / **实验性** / **未支持** / **未验证**。  
> 快照日期：2026-10-09 · 工具链：`java -jar target/jnative.jar --version` → `JNative 0.32`；`--help` 横幅 → `JNative 1.0-dev`。

## 1. 定位

| 声明 | 证据 | 状态 |
|------|------|------|
| Java 风格语法子集，AOT 编译为原生可执行文件 | `README.md`；`examples/Hello.java` → 原生二进制可运行 | 稳定（原生控制台，macOS arm64 已验证） |
| 运行时不需要 JVM/JRE | README；生成物直接执行 | 稳定 |
| 不是完整 Java SE，不消费任意 JAR | README；VS Code 扩展 README | 稳定（产品定位） |
| Maven `pom.xml` version `0.21` 与 JAR `--version` `0.32` 不一致 | `pom.xml` L10；CLI 输出 | 文档中以 CLI `--version` 为准，并写入版本页 |

## 2. 编译器与 CLI

| 能力 | 实现位置 | 验证命令 / 证据 | 状态 |
|------|----------|-----------------|------|
| 单文件编译 `.java` / `.jn` | `target/jnative.jar` | `java -jar target/jnative.jar examples/Hello.java -o …` → 打印预期字符串 | 稳定（macOS arm64） |
| 项目构建 `jnative build` | CLI + `jnative.project` | `jnative new` 后 `build` → `build/<name>` | 稳定（macOS arm64） |
| `jnative new <name>` | CLI | 生成 `jnative.toml`、`jnative.project`、`src/Main.jn`、`tests/*.jn` | 稳定 |
| `jnative doctor` | CLI | Compiler/LLVM/clang/Linker/Runtime/Package cache 全 ✓ | 稳定（本机） |
| `jnative doctor --mobile` | CLI | 依赖本机 Android/iOS SDK | 未验证（文档阶段 1 未跑移动 doctor） |
| `jnative test` | CLI | 模板含 tests；阶段 1 未系统跑通全部模板测试 | 实验性 |
| `jnative analyze` / `jnative lsp` | CLI；VS Code 调用 | 扩展 README：analyze-only 诊断 | 实验性 |
| 包管理 `add/remove/update/deps/search/…` | CLI help | 有命令；文档仅描述已验证路径 | 实验性 |
| `--target wasm32-browser` 导出核心 | 编译器 | 数独 Web；guide-tasks `verify_web.sh` PASS | 实验性 |
| WebUI `WebUI.mount` / `Html.*` | `WebUiLib` | counter 在 0.32 全量 stdlib→WAT 校验失败 | 实验性 / 当前不可用于新教程 |
| 优化 `-O0`…`-O3` | CLI | 单文件 `-O0` 已用于 Hello | 稳定 |

## 3. 标准库模块

路径：`src/main/resources/stdlib/`

| 模块目录 | 用途（据源码/命名） | API 文档状态 | 能力状态（文档口径） |
|----------|---------------------|--------------|----------------------|
| `collections` | List/Map/Set、StringBuilder、线程池等 | 索引 + 签名提取 | 实验性（广泛使用，边界待逐条验收） |
| `io` | 文件与流 | 索引 | 实验性 |
| `time` | 时间 | 索引 | 实验性 |
| `async` | 异步原语 | 索引 | 实验性 |
| `net` / `http` / `tls` | 网络 | 索引 | 实验性 |
| `db` | 数据库 | 索引 | 实验性 |
| `ui` | 桌面 UI | 索引；桌面教程阶段 2 | 实验性（macOS 有示例；Win/Linux 未验证） |
| `web` / `webui` / `browser` | Web / WASM | 索引；Web 教程阶段 2 | 实验性 |
| `json` / `math` / `crypto` / `rpc` / `graphics` / `editor` / `studio` | 扩展能力 | 索引占位 | 未验证（本阶段未逐 API 跑通） |

提取脚本：`scripts/docs/extract-api.sh` → `website/api/_generated/`。

## 4. VS Code 扩展

| 项 | 位置 | 证据 | 状态 |
|----|------|------|------|
| 扩展源码 | `tools/vscode-jnative/` | `package.json` version `0.3.0` | 稳定（源码） |
| VSIX 产物 | `jnative-0.2.3.vsix` 等；源码称 0.3.0 | 需 `npm run package` 生成最新 | 实验性（安装步骤已写；本环境 UI 宿主未验证） |
| 语言 ID `jnative`、项目识别 | README | `jnative.project` 或 `jnative.toml`+`src/` | 稳定（逻辑） |
| 构建/运行/Problems | README | JSON 报告解析 | 实验性（依赖本机 VS Code） |
| LSP 诊断 | `jnative lsp` | 无补全/跳转/重命名（README 明确） | 实验性 |
| CodeLLDB 调试 | README + `docs/jnative-native-debug-*` | macOS arm64 CLI LLDB 已验证；F5 UI 依赖扩展 | 实验性 |

## 5. 目标平台工具链

| 目标 | 实现 | 验证证据 | 状态 |
|------|------|----------|------|
| 原生 macOS arm64 | clang + LLVM | doctor ✓；Hello / guide-hello 构建运行 | 稳定 |
| 原生 macOS x86_64 | 同工具链 | 本机未测 | 未验证 |
| 原生 Linux | clang | 本交付环境未测 | 未验证 |
| 原生 Windows | — | 无本机证据 | 未验证 / 可能未支持 |
| wasm32-browser | WAT/WASM 后端 | guide-tasks / sudoku 导出核心已验证 | 实验性 |
| iOS（WKWebView 容器） | sudoku / guide-tasks `mobile/ios` | guide-tasks Simulator 构建 OK；真机 guide-tasks 未验证；数独真机曾成功 | 实验性 |
| Android（WebView 容器） | sudoku / guide-tasks `mobile/android` | guide-tasks `assembleDebug` OK；设备安装未验证 | 实验性 |

## 6. 示例与测试（文档引用）

| 示例 | 路径 | 用途 | 状态 |
|------|------|------|------|
| Hello | `examples/Hello.java` | 单文件入门 | 稳定（已跑） |
| guide-hello | `examples/guide-hello/` | 教程第 0 课项目快照 | 稳定（模板一致；构建已验证） |
| Sudoku | `examples/sudoku/` | 跨平台综合 sample | 实验性（桌面/Web/iOS 曾通；文档阶段 3 详述） |
| guide-tasks | `examples/guide-tasks/` | 桌面+Web 教程 sample | 实验性（脚本已验证） |
| WebUI counter | `examples/webui/` | WebUI 组件路径；当前构建失败 | 实验性 / 不可用 |
| debug | `examples/debug/` | 原生调试 | 实验性 |
| 单元/e2e 测试 | `src/test/java/` | 语言与后端回归 | 稳定（CI/本地 Maven；文档不替代） |

## 7. 文档体系维护来源

| 产物 | 路径 | 维护方式 |
|------|------|----------|
| 文档站 | `website/`（VitePress） | 手写中文为主；`npm run dev` / `build` |
| 事实清单 | 本文件 + 站点 `reference/fact-inventory` | 改实现后同步状态 |
| 能力矩阵 | `docs/developer/CAPABILITY_MATRIX.md` | 按平台×功能更新 |
| 阶段覆盖 | `docs/developer/STAGES.md` | 每阶段勾选 |
| API 签名 | `scripts/docs/extract-api.sh` | 从 stdlib 提取；行为说明人工核实 |
| 验证脚本 | `scripts/docs/verify-phase1.sh` | 链接/Hello 构建/API 提取 |

## 8. 明确不承诺

- Java SE 完整语义、反射、任意第三方 JAR。
- 「一份 UI 自动适配所有平台」。
- 应用商店发布流水线（未作为已验证能力）。
- 文档站点自动公开部署（仅本地 / 静态构建）。
