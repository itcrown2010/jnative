# JNative（中文）

**JNative** 是实验性的 **类 Java 语法**、**AOT 原生编译器**。

用熟悉的 Java 风格子集（`.java` / `.jn`）编写程序；编译器本身是 Java 21 程序，经私有 IR 与 LLVM，由 `clang` 链接出**原生可执行文件**。生成的程序**运行时不需要** JVM/JRE。

JNative **不是**完整 Java SE，也不加载任意第三方 JAR。

**英文为公开文档权威版本：** [README.md](README.md) · [docs/en/](docs/en/README.md)

---

## 定位

类 Java 语法 + 原生可执行文件 + 可读的编译器实现（精确 GC、效应注解、IR 优化、FFI）。不靠夸张营销；能力以实现与测试为准。

---

## 快速开始

```bash
mvn -q -DskipTests package
java -jar target/jnative.jar examples/guide-hello/src/Main.jn -o Hello
./Hello
# Hello, JNative!
```

CLI 版本以 `java -jar target/jnative.jar --version` 为准（当前常见为 **0.32**）。

完整步骤（英文）：[docs/en/getting-started.md](docs/en/getting-started.md)

---

## 中文学习路径

| 资源 | 说明 |
|------|------|
| [docs/zh-CN/](docs/zh-CN/README.md) | 中文导航 |
| [docs/tutorial/](docs/tutorial/index.md) | 循序渐进教程 |
| [website/](website/README.md) | VitePress 文档站（中文界面为主） |
| [docs/developer/FACT_INVENTORY.md](docs/developer/FACT_INVENTORY.md) | 能力 × 证据清单 |

架构与语言细节请优先读英文权威页，避免与过时中文描述冲突：  
[architecture](docs/en/architecture.md) · [language](docs/en/language.md)

---

## 贡献与许可

- [CONTRIBUTING.zh-CN.md](CONTRIBUTING.zh-CN.md) / [CONTRIBUTING.md](CONTRIBUTING.md)
- MIT — [LICENSE](LICENSE)
