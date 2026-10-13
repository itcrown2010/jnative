# JNative（中文）

**JNative** 是一个实验性的 **类 Java 语法**、**提前编译（AOT）原生编译器**项目。

它将 Java 风格源码子集（`.java` / `.jn`）经 LLVM/`clang` 编译为**原生可执行文件**。生成的程序**运行时不需要** JVM/JRE。

> **不是完整 Java SE。** 不保证兼容任意第三方 JAR。

英文主页（默认）：[README.md](README.md) · 入门：[docs/en/getting-started.md](docs/en/getting-started.md) · 中文导航：[docs/zh-CN/](docs/zh-CN/README.md)

---

## 为什么做 JNative？

定位：**类 Java 语法 + 原生可执行文件 + 现代编译器架构**。

面向想探索「Java 形状的语言如何做成原生编译器」的开发者与贡献者，而不是宣称替代完整 JDK。

---

## 现状摘要

CLI：`java -jar target/jnative.jar --version` → **JNative 0.32**（以 CLI 为准）。

| 领域 | 状态 |
|------|------|
| 词法 / 语法 / AST / 语义 / IR / LLVM → 原生可执行 | ✅（macOS arm64 已验证） |
| 类、继承、数组、转型、接口、异常、GC、FFI、线程 | ✅ |
| `wasm32-browser`、包管理 CLI | 🚧 实验性 |
| 多平台 CI / 完整 Java SE | 📋 规划中 / 非目标 |

完整英文状态表见 [README.md](README.md)。事实清单：[docs/developer/FACT_INVENTORY.md](docs/developer/FACT_INVENTORY.md)。

---

## Hello World

```java
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, JNative!");
    }
}
```

```bash
mvn -q -DskipTests package
java -jar target/jnative.jar examples/guide-hello/src/Main.jn -o Hello
./Hello
```

---

## 构建

```bash
mvn package
mvn test
java -jar target/jnative.jar doctor
```

依赖：Java 21+、Maven 3.9+、`clang` 在 `PATH` 上。

---

## 文档

- 英文权威文档：[`docs/en/`](docs/en/README.md)
- 中文教程：[`docs/tutorial/`](docs/tutorial/index.md)
- 开发者文档站：[`website/`](website/README.md)（目前中文优先）
- 贡献：[`CONTRIBUTING.zh-CN.md`](CONTRIBUTING.zh-CN.md) / [`CONTRIBUTING.md`](CONTRIBUTING.md)

---

## 许可

MIT — 见 [LICENSE](LICENSE)。原创版权说明见 [COPYRIGHT.md](COPYRIGHT.md)、[docs/legal/](docs/legal/)。
