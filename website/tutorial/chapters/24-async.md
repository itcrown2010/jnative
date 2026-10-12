# 第 24 章：async/await、线程及取消

## 本章目标

掌握：语言档案称 async/await 与线程能力存在，但 **wasm 未接通**；教程综合项目不依赖 async。桌面可用 `Thread`+`invokeLater`（见 guide-tasks）。

## 前置知识

第 1–23 章；JNative 0.32；示例在 `examples/tutorial/`。

## 概念与直观例子

async ≠ 自动非阻塞 I/O。

## 完整可运行代码

本章以概念与限制为主，不提供依赖未验证特性的假示例。

## 运行命令

无强制可运行示例；请按练习查阅链接文档。

## 代码关键部分解释

结合源码注释阅读。记住：教程以示例目录为单一事实来源，勿只抄文档中过期片段。

## 常见错误及实际诊断

- 编译失败先看 `file:line:col: error:`。
- 不要用 Java SE Javadoc 推断 JNative 行为。
- 多文件需 `jnative.project`，不是随便 javac。

## 练习

阅读 `docs/jnative-async-await.md` 限制表。

## 参考答案或可运行解法

优先对照 `examples/tutorial/` 中最接近的示例改写。综合练习见第 30 章各 stage。

## 本章完成检查

- [ ] 能向别人用自己的话解释本章概念
- [ ] 相关示例已在本机编译运行（若有）

## 下一章

[第 25 章](./25-ffi.md)
