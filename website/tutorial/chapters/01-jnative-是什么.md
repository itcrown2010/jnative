# 第 01 章：JNative 的定位、编译与运行

## 本章目标

了解 JNative 与 Java 的边界。读完后你应能独立完成章末检查项。

## 前置知识

请先完成前面章节；工具链版本 **JNative 0.32**（macOS arm64 已验证）。

JNative 是一门 **Java 风格语法** 的 **独立原生语言**：源码经 AOT 编译为原生可执行文件，**运行时不需要 JVM**。

它 **不是** Java SE，也 **不** 消费任意 JAR。同名 API 可能行为不同。

## 编译与运行（直观例子）

```bash
java -jar target/jnative.jar examples/tutorial/01-hello/Main.jn -o /tmp/hello
/tmp/hello
```

仓库也可使用 `./scripts/jnative.sh` 包装。

## 迁移提醒

若你熟悉 Java：请忘掉 classpath / JAR / 反射生态；把 JNative 当成新语言学习。




## 代码关键部分解释

见上文注释与「概念」段。示例以 `examples/tutorial/` 为唯一事实来源。

## 常见错误及实际诊断

- 找不到 `target/jnative.jar`：先在仓库根 `mvn -DskipTests package`。
- 路径错误：命令中的相对路径相对于仓库根。

## 练习

1. 修改 Hello 输出为自己的名字并重新运行。
2. 故意制造一个类型错误，读懂诊断中的行号。

## 参考答案或可运行解法

练习 1：改 `System.out.println` 字符串即可。练习 2：参见 `05-errors/Bad.jn`。

## 本章完成检查

- [ ] 能说出运行时不需要 JVM

## 下一章

[第 02 章](./02-安装与-vscode.md)（见目录 [index](/tutorial/)）
