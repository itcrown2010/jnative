# 第 02 章：安装及 VS Code 配置

## 本章目标

doctor 通过。读完后你应能独立完成章末检查项。

## 前置知识

请先完成前面章节；工具链版本 **JNative 0.32**（macOS arm64 已验证）。

完整步骤见开发者文档入门指南（已验证路径）：

- [安装 SDK](/guide/install)
- [检查工具链](/guide/doctor)
- [VS Code 插件](/guide/vscode)

本教程假定你已能执行：

```bash
java -jar target/jnative.jar --version   # JNative 0.32
java -jar target/jnative.jar doctor      # Environment healthy
```

**补全**：当前扩展 **不提供** 智能补全；有高亮与构建/LSP 诊断。




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

- [ ] 配置好 jarPath

## 下一章

[第 03 章](./03-hello-world.md)（见目录 [index](/tutorial/)）
