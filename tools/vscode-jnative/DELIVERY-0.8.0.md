# VS Code 扩展 0.8.0 交付

## 产物

- `tools/vscode-jnative/jnative-0.8.0.vsix`
- 欢迎页：`JNative: 打开入门`
- 模板：`templates/web-adder`、`templates/desktop-adder`；移动用 CLI `--template mobile`
- 随包文档：`docs/GETTING_STARTED.md`
- 矩阵：`SUPPORT_MATRIX.md`

## 本轮实现

1. **编辑基础**：沿用 LSP analyze-only / Problems / 失败禁跑；修复创建冲突检测覆盖 `Main.jn`；Task build 对齐 `--target`。
2. **欢迎页**：版本、工具链分项状态、三类创建、教程、最近项目、可关闭自动显示。
3. **工具链**：分目标检测（SDK / 原生 / WASM / Android / Xcode / CodeLLDB）；不静默下载。
4. **创建向导**：桌面 / Web / 移动；冲突不覆盖；同一加法业务。
5. **统一操作**：创建 / 检查工具链 / 选择目标 / 构建 / 运行 / 调试 / 打包 / 停止 / 打开教程。
6. **生命周期**：Web 预览端口冲突自动改端口；停止仅杀本扩展启动的进程。

## 验收结果（本机 2026-10-09 macOS arm64）

| 步骤 | 结果 |
|------|------|
| Web 模板 `build --target wasm32-browser` | **通过** → `dist/app.wasm` |
| 桌面模板 `build --project` | **通过** → `.jnative/build/app` |
| 移动创建 | 走 CLI 模板（此前已有）；本轮未重跑模拟器 |
| Problems / 失败禁跑 | 代码路径保持；Web 失败删除 stamp 不预览 |
| 全新 VSIX 安装 UI 截图 | 见 `verify-artifacts/ui-welcome.html`（静态示意） |

## 剩余阻断

- Web/WASM、移动 **无** CodeLLDB 源码调试。
- `jnative build --project --target wasm32-browser` 对简单项目仍可能偏向 native 产物；扩展 Web 路径改走单文件 wasm 构建规避。
- Android/iOS 模拟器运行依赖本机 SDK，未在本轮全新环境重验。
- 字节级可复现 / 商店分发：未做。

## 里程碑对照

1. Web 创建与运行闭环 — **完成（构建已验；预览代码已接）**
2. 桌面创建、运行、调试 — **创建/构建完成；调试沿用 CodeLLDB**
3. 移动工具链与模拟器 — **创建与命令已接；模拟器运行标为未在本轮重验**
