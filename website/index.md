---
layout: home
hero:
  name: JNative
  text: Java 风格的独立原生语言
  tagline: AOT 编译为原生程序 · 运行时不需要 JVM · 不是 Java SE / 不消费任意 JAR
  actions:
    - theme: brand
      text: 从安装开始
      link: /guide/what-is-jnative
    - theme: alt
      text: 语言指南
      link: /language/
    - theme: alt
      text: API 索引
      link: /api/
features:
  - title: 入门可验证
    details: 安装、doctor、VS Code、第一个 .jn 项目与 Hello 教程均绑定实际 CLI 与本机验证记录。
  - title: 能力状态诚实
    details: 稳定 / 实验性 / 未支持 / 未验证，按功能与平台标注，不因「有接口」就宣称可用。
  - title: 分阶段覆盖
    details: 第一阶段：安装与语言基础；第二阶段：桌面与 Web；第三阶段：移动与跨平台 sample。
---

## 当前文档版本

对应工具链：**JNative 0.32**（`java -jar target/jnative.jar --version`），CLI 横幅 **1.0-dev**。

| 你想做什么 | 去哪里 |
|------------|--------|
| 安装并跑通 Hello | [入门指南](/guide/what-is-jnative) |
| **系统学习语言** | [JNative 语言教程](/tutorial/) |
| 查语法与限制 | [语言开发指南](/language/) |
| 查标准库 | [API 参考](/api/) |
| 跟做桌面/Web/移动 | [应用教程](/tutorials/) |
| 看平台支持 | [支持矩阵](/reference/support-matrix) |

```bash
# 最快验证编译器（仓库已构建时）
java -jar target/jnative.jar --version
java -jar target/jnative.jar doctor
java -jar target/jnative.jar examples/Hello.java -o /tmp/Hello && /tmp/Hello
```
