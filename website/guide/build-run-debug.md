# 构建、运行与调试

## 单文件模式

```bash
jnative examples/Hello.java -o /tmp/Hello
/tmp/Hello
# hello world is the string
```

常用标志：

| 标志 | 含义 |
|------|------|
| `-o out` | 输出路径 |
| `-O0`…`-O3` | 优化级别（默认偏优化；调试用 `-O0` 或 `--debug`） |
| `--run` | 编译后运行 |
| `--debug` | 调试构建（配合 LLDB / CodeLLDB） |
| `--format json` | 机器可读构建报告（VS Code Problems 使用） |

## 项目模式

```bash
cd my-app
jnative build [--project .] [--debug|--release]
jnative run [--project .]
jnative test [--project .]
```

| 命令 | 状态 |
|------|------|
| `build` / 直接运行产物 | **稳定**（macOS arm64，guide-hello） |
| `run` | CLI 提供；与扩展「运行项目」同类 |
| `test` | **实验性**（模板测试用 `1/ok` 断言风格） |

## VS Code

| 命令 | 行为 |
|------|------|
| 构建项目 | `jnative build --project <root> --format json` |
| 运行项目 | 先构建；失败则 **不** 运行旧产物 |
| 构建当前文件 | 单文件模式 |
| 调试项目 | `--debug` 构建 → CodeLLDB（需自行安装） |

控制台「运行」**不是**断点调试。

## 命令行 LLDB（macOS arm64：实验性但有证据）

```bash
jnative my-app/src/Main.jn --debug -O0 -o /tmp/my-app-dbg
lldb /tmp/my-app-dbg
```

已验证能力（见仓库 `docs/jnative-native-debug-report.md`）：断点、单步、栈、部分标量类型。  
未做：对象字段可视化、异步栈、WASM/移动调试。

## 下一步

[补全、诊断与日志](/guide/diagnostics)。
