# 查看补全、诊断和日志

## 现实能力（请按此预期）

| 能力 | 状态 | 说明 |
|------|------|------|
| TextMate 语法高亮 | 实验性 | 语言 ID `jnative` |
| 构建错误 → Problems | 实验性 | JSON 报告；行号 1-based→0-based |
| LSP 前端诊断 | 实验性 | `jnative lsp` / analyze-only；防抖 |
| **补全** | **未支持** | 扩展 README 明确：本轮不做空壳补全 |
| 跳转 / 重命名 | **未支持** | 同上 |

不要期待「完整 Java 语言服务」。

## 查看日志

- VS Code：**「JNative: 显示语言服务器日志」** / 输出通道「JNative」
- CLI：`jnative analyze path/to/File.jn`（或 `--format json`）
- 构建失败：终端中的 `file:line:col: error: …` 格式可被 problem matcher 解析

## stdlib 诊断

合并编译单元时，来自标准库拼接的诊断可能标为 `[stdlib]`，避免错误行落到用户代码。

## Workspace Trust

未信任工作区：允许高亮；禁止启动编译器、LSP、构建与运行。

## 下一步

[添加第三方源码包](/guide/packages)。
