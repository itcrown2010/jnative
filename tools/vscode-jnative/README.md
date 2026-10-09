# JNative VS Code 扩展

作者：张晓达

在 VS Code 中创建、编辑、构建并运行 **JNative** 原生控制台程序。

> 编译工具需要 Java；生成的应用运行时 **不需要 JVM**。  
> JNative 只支持语言子集，**不是**完整 Java SE，也不消费任意 JAR。

## 安装

1. 构建仓库 CLI：`mvn -q -DskipTests package`（生成 `target/jnative.jar`）。
2. 安装扩展 VSIX：

先打包（会带上 `vscode-languageclient`，勿加 `--no-dependencies`）：

```bash
cd tools/vscode-jnative && npm install && npm run package
```

再安装：

```bash
"/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code" \
  --install-extension tools/vscode-jnative/jnative-0.2.1.vsix
```

或在 VS Code：扩展视图 → `...` → Install from VSIX。

## 工具链配置（优先级）

1. `jnative.executablePath` — 可执行入口 / `scripts/jnative.sh`
2. `jnative.jarPath` + `jnative.javaPath` — `java -jar …`
3. PATH 中的 `jnative`
4. 工作区向上查找的 `scripts/jnative.sh`

推荐（本仓库开发）：

```json
{
  "jnative.jarPath": "/绝对路径/jnative/target/jnative.jar",
  "jnative.javaPath": "java"
}
```

命令 **「JNative: 检查工具链」** 会实际执行 `jnative --version` / `doctor`，并探测 Java 与 clang（不只检查文件是否存在）。

未配置时会提示缺失项并提供打开设置的操作。扩展 **不会** 后台自动下载软件或跑 Maven。

## 语言识别

- 独立语言 ID：`jnative`（TextMate 高亮 + 语言配置）。
- 仅当文件位于已识别 JNative 项目的 **源码根** 内时，打开 `.java` 才会切到 `jnative`。
- 识别条件：目录含 `jnative.project`，或同时含 `jnative.toml` + `src/`。
- **不会**仅因工作区某处有 `jnative.toml` 就接管所有 Java 文件。
- 命令 **「JNative: 将当前文件作为 JNative 打开」** 可手动切换。
- 多根工作区按文件所属项目分别处理。

## 创建项目

**「JNative: 创建项目」** → 选择父目录与名称 → 调用：

```bash
jnative new <name> --dir <parent> --minimal
```

生成：

```text
hello-jnative/
├── jnative.toml
├── jnative.project   # 构建器清单（与现有 CLI 兼容）
├── .gitignore
└── src/Main.java     # 打印 Hello JNative
```

创建前检查冲突，不覆盖已有文件。

## 构建与运行

| 命令 | 行为 |
|------|------|
| 构建项目 | `jnative build --project <root> --format json` |
| 运行项目 | 先构建；失败则 **不** 运行旧产物；成功后按报告中的 artifact 启动 |
| 构建当前文件 | 单文件模式 `jnative File.java -o … --format json` |
| 停止运行 | 终止插件启动的进程 |
| 显示输出 | 打开 JNative 输出通道 |
| 打开项目清单 | 打开 `jnative.project` 或 `jnative.toml` |

使用 VS Code Task 类型 `jnative`（可在 `tasks.json` 中配置）。参数以独立 argv 传递，不经 shell 拼接。同一项目构建有锁，避免并发冲突。

控制台程序运行 **不是** 断点调试。

## 诊断

- 构建 JSON 报告写入 Problems（文件 / 行 / 列 / 严重级别 / 消息）。
- 成功构建清除该项目过期诊断。
- 行号：编译器 1-based → VS Code 0-based。
- stdlib 拼接产生的诊断会标为 `[stdlib]`，避免错误落到用户行。
- 可选 LSP（`jnative lsp`）：对编辑器 **当前文本** 做 analyze-only 诊断（防抖、版本丢弃）。日志在 stderr。

### LSP 限制

- 单缓冲前端分析；多文件 / 第三方源码库 / 生成源码的完整解析能力与编译器一致，不伪造符号表。
- 无补全、跳转、重命名、调试（本轮不做空壳命令）。

## Workspace Trust

- 未信任：允许高亮；禁止启动编译器、LSP、构建与运行。
- 工作区级可执行路径与参数仅在受信任后使用。

## 快捷键

可自行绑定命令 ID：`jnative.buildProject`、`jnative.runProject` 等。

## 开发与打包

```bash
cd tools/vscode-jnative
npm install
npm run compile
npm run lint
node out/test/runUnit.js          # 单元测试（项目识别 / JSON 报告）
npm run package
```

## 调试（源码级 MVP）

需要安装 [CodeLLDB](https://marketplace.visualstudio.com/items?itemName=vadimcn.vscode-lldb)（`vadimcn.vscode-lldb`）。扩展**不会**自动安装。

| 命令 | 行为 |
|------|------|
| JNative: 调试项目 | `--debug` 构建 → CodeLLDB；失败不启旧产物 |
| JNative: 生成调试配置 | 仅当缺少 `.vscode/launch.json` 时写入（不覆盖） |
| F5（无配置） | 动态提供 lldb 配置并先调试构建 |

`jnative.debugEnv` 可传环境变量；`runArguments` 作为程序参数。路径与参数以独立 argv 传递，不经 shell 拼接。

中文教程：`docs/jnative-native-debug-tutorial.md`  
支持矩阵：`docs/jnative-native-debug-report.md`  
示例：`examples/debug/`、`examples/debug/multifile/`

已验证（命令行 LLDB）：macOS arm64 断点 / 单步 / 栈 / int·long·boolean；跨文件源码映射。  
VS Code F5 UI：依赖本机安装 CodeLLDB；本交付环境无 `code` CLI 时见报告中的阻塞项。  
未做：对象字段可视化、异步栈、优化代码变量恢复、WASM/移动调试。

## 已知限制

- 非完整 Java SE；无任意 JAR。
- 合并编译单元下的诊断映射在复杂多文件场景可能需后续增强。
- UI 级「安装 → 创建 → 运行 → Problems」若本机未跑 VS Code 集成宿主，见交付报告中的未验证项。
