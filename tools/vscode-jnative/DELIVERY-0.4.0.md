# JNative VS Code 插件 0.4.0 交付说明

作者：张晓达

## 基础回归（0.3.0 能力）

在更新语言服务 JAR 后执行：

```bash
mvn -q -Dtest=CompletionEngineTest,IdeServicesTest test
mvn -q -DskipTests package
node tools/vscode-jnative/scripts/lsp-smoke.mjs
```

结果：`verify-artifacts/smoke-summary.json` — **19 passed / 0 failed**

含：未保存补全、成员补全、参数提示、编辑/构建 Problems、跳转、引用、悬停、Outline、工作区符号、跨文件（包缓存）跳转。

版本对齐：

| 组件 | 版本 |
|------|------|
| 扩展 `package.json` | **0.4.0** |
| CLI / LSP (`jnative --version`) | **0.32** |
| VSIX | `tools/vscode-jnative/jnative-0.4.0.vsix` |
| 语言服务入口 | `java -jar <jnative.jarPath> lsp` |

请将 `jnative.jarPath` 指向本仓库刚打包的 `target/jnative.jar`，避免扩展 0.4.0 仍连旧 JAR。

## 本轮新增

| 能力 | 实现要点 |
|------|----------|
| 跳转定义 | `IdeServices.definition` ← `SemanticModel.symbolAt` + `declarationOf`；跨文件靠项目源码 + `.jnative/pkg-cache/extracted` |
| 查找引用 | `referencesOf`；按符号身份（含方法签名）匹配，非名称搜索 |
| 悬停 | 签名 + 源码中已有 `/** */`（不编造 API 文档） |
| Outline / 工作区符号 | AST / ClassSymbol 索引；打开缓冲可参与 |
| 快速修复 | 唯一候选时添加 import；移除明确未使用 import；多候选列出由用户选；WorkspaceEdit |

导航与诊断共用 `analyzeUnits`（前端 only，不触发原生构建）。

## 修改文件

- `SemanticModel.java` — 局部/参数/构造器声明；重载身份
- `JNativeCompiler.java` — `analyzeUnits`
- `ProjectSourceCollector.java` — 项目与包缓存源码收集
- `IdeServices.java` — 导航 / 悬停 / 符号 / code action
- `LspServer.java` — 能力声明与请求处理
- `IdeServicesTest.java` / `lsp-smoke.mjs`
- `package.json` → 0.4.0

## 安装升级

```bash
cd /Users/zhangxiaoda/jnative
mvn -q -DskipTests package
cd tools/vscode-jnative && npm install && npm run package

"/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code" \
  --uninstall-extension jnative.jnative
"/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code" \
  --install-extension ./jnative-0.4.0.vsix --force
```

设置：

```json
{
  "jnative.jarPath": "/Users/zhangxiaoda/jnative/target/jnative.jar",
  "jnative.javaPath": "java",
  "jnative.enableLanguageServer": true
}
```

确认扩展 **0.4.0**，状态栏 `JNative LS` 就绪；「重启语言服务」后功能恢复且不重复注册提供器。

## 截图与证据

- `verify-artifacts/screenshots/`（验收页 / UI 示意）
- `verify-artifacts/definition-crossfile.json` 等协议产物
- `verify-artifacts/acceptance.html`

## 支持边界

- 引用：仅语义已解析的表达式；不含字符串/注释文本匹配；反射/动态调用不覆盖。
- 第三方：仅已进入包缓存或项目源码根的源码包；未解析依赖会提示「定义源码不可用」，不跳错文件。
- 快速修复：不猜多候选；不做跨项目重命名/格式化。
- 性能：项目级 analyze 有缓存；大型项目首次可能数百 ms（stderr 打印 `analyzeUnits … ms=`）。
- 本机无 `code` CLI 时，EDH 目视需用户本地确认；协议级验收已覆盖。
