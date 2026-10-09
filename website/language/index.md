# 语言开发指南

本章描述 **当前实现** 中的语言子集。每一节包含：概念、最小示例、语义要点、常见错误、支持限制，以及相关 API / 教程 / 测试线索。

## 阅读约定

- 示例默认可在 **原生 macOS arm64** 用 `jnative File.jn -o …` 验证；其他平台见支持矩阵。
- **未实现** 的语法放在各节「限制」或汇总页 [限制与路线](/language/limits)，**不**提供看似可运行的假代码。
- JNative **不是** Java SE：同名 API 行为可能不同。

## 章节列表

1. [文件、模块与可见性](/language/modules)
2. [基础类型与变量](/language/types)
3. [数值运算](/language/numbers)
4. [字符串与 Unicode](/language/strings)
5. [控制流](/language/control-flow)
6. [类、继承与接口](/language/classes)
7. [枚举与 final](/language/enums)
8. [泛型](/language/generics)
9. [集合与遍历](/language/collections)
10. [Lambda 与方法引用](/language/lambdas)
11. [异常与资源](/language/exceptions)
12. [异步与线程](/language/async)
13. [FFI 与原生内存](/language/ffi)
14. [GC 与线程安全](/language/gc)

建议先完成 [Hello 教程](/tutorials/hello/)，再按需深入。
