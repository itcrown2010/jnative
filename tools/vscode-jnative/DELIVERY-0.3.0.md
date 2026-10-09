# JNative VS Code 插件 0.3.0 交付说明

作者：张晓达

## 两个核心问题的根因

### 1. 编辑代码没有自动完成提示

| 层 | 根因 |
|----|------|
| LSP 能力 | `initialize` 未声明 `completionProvider` / `signatureHelpProvider`，客户端不会发补全请求 |
| 分析管线 | `analyze` 在存在语法/类型错误时丢弃 `SemanticModel`，残缺输入（`obj.`、`add(1,`）得不到符号 |
| 局部变量 | 仅从未引用表达式的 `resolvedSymbols` 收集，未声明后立即补全不到 |
| 残缺缓冲 | `patchBalanced` 把 `)` 追加到文件末尾，破坏 `add(1,\n}}` 结构；尾逗号与缺 `;` 导致整类解析失败 |

### 2. 编译失败没有错误提示 / 无法定位

| 层 | 根因 |
|----|------|
| 构建 UX | 失败时仅写 Output，或用 `showInformationMessage`，无 Problems 操作入口 |
| 结构化结果 | 未稳定消费 `--format json`；工具链缺失时静默 |
| 运行路径 | 失败后仍可能用旧产物（已改为必须 `success && artifact`） |

## 修改文件与目的

| 文件 | 目的 |
|------|------|
| `src/main/java/io/jnative/lsp/CompletionEngine.java` | 语义补全 / 签名提示；未保存文本 patch；作用域/成员/静态成员 |
| `src/main/java/io/jnative/lsp/LspServer.java` | 声明 completion/signatureHelp；处理请求 |
| `src/main/java/io/jnative/compiler/JNativeCompiler.java` | lenient analyze，错误时仍保留 SemanticModel |
| `src/main/java/io/jnative/frontend/MultiUnitFrontend.java` | lenient 前端分析 |
| `src/test/java/io/jnative/lsp/CompletionEngineTest.java` | 局部 / 成员 / 签名单测 |
| `tools/vscode-jnative/src/lspClient.ts` | 状态栏、日志通道、重启、失败可操作提示 |
| `tools/vscode-jnative/src/diagnostics.ts` | 构建 JSON → Problems；孤儿消息不伪造 1:1 |
| `tools/vscode-jnative/src/runner.ts` | 保存脏缓冲、通知、失败禁跑 |
| `tools/vscode-jnative/src/extension.ts` | 日志/重启命令注册 |
| `tools/vscode-jnative/package.json` | **0.3.0**、命令与激活事件 |
| `tools/vscode-jnative/scripts/lsp-smoke.mjs` | 真实 stdio LSP + build JSON 验收 |
| `tools/vscode-jnative/README.md` | 安装与语言服务说明 |

## VSIX

路径：`tools/vscode-jnative/jnative-0.3.0.vsix`

## 安装 / 升级（避免仍用旧版）

```bash
# 1) 编译器（语言服务与构建共用）
cd /Users/zhangxiaoda/jnative
mvn -q -DskipTests package

# 2) 打 VSIX
cd tools/vscode-jnative
npm install && npm run package

# 3) 卸载旧版再装（版本号必须看到 0.3.0）
"/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code" \
  --uninstall-extension jnative.jnative
"/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code" \
  --install-extension ./jnative-0.3.0.vsix --force
```

或：扩展视图 → `...` → Install from VSIX → 选 `jnative-0.3.0.vsix`。

工作区设置（推荐）：

```json
{
  "jnative.jarPath": "/Users/zhangxiaoda/jnative/target/jnative.jar",
  "jnative.javaPath": "java",
  "jnative.enableLanguageServer": true
}
```

确认：状态栏显示 `JNative LS` 就绪；命令面板有「显示语言服务日志」「重启语言服务」。

## 验收结果（真实 LSP / 构建，非符号表假数据）

本机执行：

```bash
mvn -q -Dtest=CompletionEngineTest test
node tools/vscode-jnative/scripts/lsp-smoke.mjs
```

`verify-artifacts/smoke-summary.json`：**7 passed / 0 failed**

| 项 | 结果 |
|----|------|
| initialize completion/signature | PASS |
| 未保存局部变量 `answer` | PASS（`completion-locals.json`） |
| `b.` 成员 `value`/`bump` | PASS（`completion-members.json`） |
| `add(1,` 参数提示 active=1 | PASS（`signature-help.json`） |
| 类型错误 publishDiagnostics | PASS（行/列可定位） |
| `build --format json` → 诊断 | PASS（`Main.java:3:9` 类型不兼容） |
| 失败不跑旧产物 | 代码路径：`runProject`/`debugProject` 检查 `built.success` |

证据页：`tools/vscode-jnative/verify-artifacts/acceptance.html`  
截图目录：`tools/vscode-jnative/verify-artifacts/screenshots/`

## 剩余限制

- 自动导入、重命名重构未做（按里程碑延后）。
- 超大型项目补全延迟取决于 analyze；无增量缓存。
- 本机无 `code` CLI 时，UI 截图来自验收页 + 协议产物；请在 Extension Development Host 中再目视确认状态栏与 Problems。
- Java 标准库补全不做固定列表冒充；仅当前项目/编译器可见类型。

## 命令速查

- **JNative: 显示语言服务日志**
- **JNative: 重启语言服务**
- **JNative: 构建项目**（失败 → 通知 + Problems / 输出）
