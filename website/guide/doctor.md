# 检查工具链

## 桌面 / 原生工具链

```bash
jnative doctor
# 或
java -jar target/jnative.jar doctor
```

本机（2026-10-09，macOS arm64）成功输出形态：

```text
✓ Compiler
✓ LLVM
✓ clang
✓ Linker
✓ Runtime
✓ Package cache

Environment healthy.
```

任一红叉时：先保证 `mvn package` 成功、`clang` 在 `PATH`、LLVM 与 doctor 期望版本匹配。不要用「删除整个仓库」作为第一步；见 [常见安装问题](/guide/troubleshooting-install)。

## 移动工具链

```bash
jnative doctor --mobile
# 或
jnative doctor --with-mobile
```

| 项 | 状态 |
|----|------|
| 命令存在 | 是（CLI `--help`） |
| 本文档阶段 1 执行结果 | **未验证** |

请在配置 Android SDK / Xcode 后自行执行，并把结果记入你的环境笔记；通过后再跟移动教程。

## VS Code 内检查

扩展命令：**「JNative: 检查工具链」** — 会实际执行 `--version` / `doctor`，并探测 Java 与 clang（不只检查文件是否存在）。

## 下一步

[安装并配置 VS Code 插件](/guide/vscode)。
