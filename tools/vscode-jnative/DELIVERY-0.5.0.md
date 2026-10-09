# JNative VS Code 插件 0.5.0 交付说明

作者：张晓达

## 基础回归

```bash
mvn -q -Dtest=CompletionEngineTest,IdeServicesTest,RenameServiceTest,SourceFormatterTest test
mvn -q -DskipTests package
node tools/vscode-jnative/scripts/lsp-smoke.mjs
```

协议烟测目标：补全 / Problems / 定义 / 引用 / 重命名 / 格式化均 PASS。

| 组件 | 版本 |
|------|------|
| 扩展 | **0.5.0** |
| CLI/LSP | **0.32**（`target/jnative.jar`） |
| VSIX | `tools/vscode-jnative/jnative-0.5.0.vsix` |

## 本轮能力

### 安全重命名（prepareRename + rename）

- 局部变量、参数、字段、static/private 方法、项目内类型（不改文件名）。
- 基于 `SemanticModel.referencesOf` 符号身份；校验标识符与作用域冲突。
- 不改字符串/注释；第三方 `pkg-cache` / 不可写路径拒绝。
- 非 private 实例方法（可能覆盖）明确拒绝，不返回残缺 WorkspaceEdit。
- WorkspaceEdit，支持预览与撤销；分析过期时文本对不齐则失败。

### 源码格式化

- `SourceFormatter`：词法+语法校验失败则拒绝；保留字面量、注释内容、CRLF/LF。
- 仅完整文档格式化；不默认 formatOnSave。
- 设置：`jnative.formatIndentSize`、`jnative.formatInsertSpaces`。

### 项目操作

- 多项目 QuickPick（取消则不构建/运行）；当前文件所属项目优先提示。
- 既有：创建、工具链检查、构建/运行/停止、调试、清单、输出、重启 LS。

## 支持矩阵

| 场景 | 状态 |
|------|------|
| 局部/参数重命名 | 支持 |
| 字段重命名 | 支持 |
| static / private 方法 | 支持 |
| 可覆盖实例方法 | **拒绝**（明确消息） |
| 构造器 | **拒绝** |
| 第三方源码包符号 | **拒绝** |
| 类型重命名（不改文件名） | 支持 |
| 文档格式化 | 支持 |
| 范围格式化 | **未开放** |
| 保存时格式化 | 默认关（用户自行配置） |

## 升级

```bash
cd /Users/zhangxiaoda/jnative && mvn -q -DskipTests package
cd tools/vscode-jnative && npm install && npm run package
code --uninstall-extension jnative.jnative
code --install-extension ./jnative-0.5.0.vsix --force
```

确认扩展 **0.5.0** 且 `jnative.jarPath` 指向本仓库 `target/jnative.jar`。

## 截图与证据

- `verify-artifacts/screenshots/`
- `verify-artifacts/rename.json`、`formatting.json`、`smoke-summary.json`
