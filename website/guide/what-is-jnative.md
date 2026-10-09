# JNative 是什么

JNative 是一门 **Java 风格语法** 的 **独立原生语言** 工具链：源码经词法/语法/语义分析与自有 IR，再经 LLVM/clang **提前编译（AOT）** 为原生可执行文件（或 wasm32-browser 等目标）。

## 适用范围

适合：

- 需要原生启动速度与部署形态的控制台工具、服务端程序（实验性网络/数据库能力）
- 桌面 UI（`jnative.ui`，当前以 macOS 验证为主）
- 浏览器 WASM / WebUI（实验性）
- 移动端 **WebView/WKWebView 容器** 承载 WASM UI（实验性；不是原生控件 UI）

不适合 / 不承诺：

- 完整 **Java SE** 兼容或任意 **JAR** 依赖复用
- 「写一份 UI 自动适配所有平台」
- 未标注平台上的生产发布保证

## 与「Java」的关系

| 方面 | 说明 |
|------|------|
| 语法观感 | 类 Java：`class`、`main`、`System.out.println` 等子集 |
| 源文件扩展名 | `.jn`（项目模板）或 `.java`（编译器同样接受） |
| 运行时 | **不需要** JVM/JRE |
| 标准库 | 仓库内 `src/main/resources/stdlib/`，由编译器嵌入，不是 OpenJDK |

## 文档如何标注能力

- **稳定**：本版本有明确验收与执行证据
- **实验性**：可用但有明确限制
- **未支持**：当前不能使用
- **未验证**：缺少对应环境或执行证据

详见 [支持矩阵](/reference/support-matrix) 与 [事实清单](/reference/fact-inventory)。

## 下一步

继续阅读 [运行依赖](/guide/runtime-deps)。
