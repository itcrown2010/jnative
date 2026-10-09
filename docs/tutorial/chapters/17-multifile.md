# 第 17 章：多文件、package/import

## 本章目标

掌握：多源码根由 `jnative.project` 的 `source` 列出；同编译单元可互相见。显式 import 支持，无通配符 import。

## 前置知识

第 1–16 章；JNative 0.32；示例在 `examples/tutorial/`。

## 概念与直观例子

本例同默认包多文件。

## 完整可运行代码

文件：`examples/tutorial/17-multifile/Main.jn`（整文件）

```jn

```

## 文件名及运行命令

```bash
java -jar target/jnative.jar examples/tutorial/17-multifile/Main.jn -o /tmp/17-multifile -O0
/tmp/17-multifile
```

## 精确预期输出

```text
[ok]
```

## 代码关键部分解释

结合源码注释阅读。记住：教程以示例目录为单一事实来源，勿只抄文档中过期片段。

## 常见错误及实际诊断

- 编译失败先看 `file:line:col: error:`。
- 不要用 Java SE Javadoc 推断 JNative 行为。
- 多文件需 `jnative.project`，不是随便 javac。

## 练习

把 Util 挪到另一文件并 build。

## 参考答案或可运行解法

优先对照 `examples/tutorial/` 中最接近的示例改写。综合练习见第 30 章各 stage。

## 本章完成检查

- [ ] 能向别人用自己的话解释本章概念
- [ ] 相关示例已在本机编译运行（若有）

## 下一章

[第 18 章](./18-packages.md)
