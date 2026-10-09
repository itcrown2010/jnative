# 文件、模块与可见性

## 概念

- 源文件：`.jn` 或 `.java`，通常一个顶层 `public class` 与文件名对应（项目 `main` 在 `jnative.project` 中指定）。
- 项目：`jnative.project` 的 `source` 数组列出源码根；构建器合并编译。
- 包清单：`jnative.toml` 描述包名、版本、`type`（如 `source`）。
- **可见性**：支持 Java 风格的 `public` 等修饰；完整模块系统 / JPMS **未作为稳定能力**。

## 最小示例

见 `examples/guide-hello/src/Main.jn`。多文件时把额外 `.jn` 放在 `source` 根下，由 `main` 类引用。

## 明确语义

- CLI `new` 默认主类名 `Main`，输出 `build/<name>`。
- import 行为以编译器当前语义为准；不要假设完整 `classpath` 或 JAR。

## 常见错误

- 在无 `jnative.project` 的目录期望「自动多文件工程」。
- 把 OpenJDK 的 `package`/`module-info` 习惯原样搬过来。

## 支持限制

| 项 | 状态 |
|----|------|
| 单文件 / 简单多文件项目 | 实验性→入门路径稳定 |
| 完整 Java 模块系统 | 未支持 |
| 任意 JAR 依赖 | 未支持 |

## 相关链接

- [第一个项目](/guide/first-project)
- 测试：仓库 `src/test/java` 中与解析/项目相关的用例（维护者向）
