# 编译工具与运行依赖

## 编译期（构建 SDK / 编译用户程序）

| 依赖 | 用途 | 验证状态 |
|------|------|----------|
| **Java 21+** | 运行 `jnative.jar`（编译器本身是 JVM 程序） | 本仓库开发机已用；请本机 `java -version` 确认 |
| **Maven 3.9+** | 从源码打包 `target/jnative.jar` | 稳定路径：`mvn -DskipTests package` |
| **clang**（在 `PATH`） | 链接原生可执行文件 | `jnative doctor` 检查 |
| **LLVM** | IR 后端 | `doctor` 检查 |

可选：

| 依赖 | 用途 | 状态 |
|------|------|------|
| Android SDK / Gradle | `jnative mobile` Android | 未验证（阶段 1） |
| Xcode / 模拟器或真机 | iOS 容器 | 实验性（数独曾装真机） |
| 浏览器 | 预览 wasm32-browser | 实验性 |
| VS Code + CodeLLDB | 编辑与原生调试 | 实验性 |

## 运行期（你编译出的程序）

| 目标 | 运行依赖 |
|------|----------|
| 原生可执行文件 | **不需要** JVM；依赖系统动态库（由链接器决定，通常为平台 C 运行时） |
| wasm32-browser | 现代浏览器 + 配套 JS 胶水（由构建产物提供） |
| 移动容器 | 系统 WebView / WKWebView + 打包进 App 的 www/WASM 资源 |

## 版本号说明

同一次安装可能看到：

- `java -jar target/jnative.jar --version` → **JNative 0.32**
- `--help` 横幅 → **JNative 1.0-dev**
- 仓库 `pom.xml` `<version>` → **0.21**（与 JAR 版本可能不同步）

**文档与验收以 `--version` 为准**，并在 [版本说明](/reference/versions) 记录差异。

## 下一步

[安装 SDK](/guide/install)。
