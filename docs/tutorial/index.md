# JNative 语言教程

作者：张晓达

循序渐进的正式教程：读完后应能**独立编写、组织、调试**一个中等规模命令行程序（任务管理器）。

| 项 | 值 |
|----|-----|
| 适用版本 | **JNative 0.32**（`java -jar target/jnative.jar --version`） |
| 验证平台 | macOS arm64（其他平台未在本教程验收） |
| 源码后缀 | `.jn`（与 `.java` 同等，模板默认 `.jn`） |
| 示例事实来源 | 仓库路径 `examples/tutorial/`（源码单一事实来源） |
| 章节—实现对应 | [MAPPING.md](./MAPPING.md) |
| 验证脚本 | `./scripts/docs/verify-tutorial.sh` |
| 验证报告 | [VERIFICATION.md](./VERIFICATION.md) |

> **不是** Java SE / JAR 教程。不要照搬 OpenJDK 示例。

## 学习路径

### 第一部分：开始编程

1. [JNative 是什么](./chapters/01-jnative-是什么.md)
2. [安装与 VS Code](./chapters/02-安装与-vscode.md)
3. [Hello World](./chapters/03-hello-world.md)
4. [项目与入口](./chapters/04-项目与入口.md)
5. [读懂编译错误](./chapters/05-编译错误.md)

### 第二部分：基础语法

6. [变量与类型](./chapters/06-vars-types.md)
7. [数值与边界](./chapters/07-numbers.md)
8. [字符串](./chapters/08-strings.md)
9. [运算符与转换](./chapters/09-operators.md)
10. [控制流](./chapters/10-control.md)
11. [方法与递归](./chapters/11-methods.md)
12. [数组](./chapters/12-arrays.md)

### 第三部分：组织程序

13. [对象](./chapters/13-objects.md)
14. [封装](./chapters/14-access.md)
15. [继承与接口](./chapters/15-inherit.md)
16. [enum 与 final](./chapters/16-enum-final.md)
17. [多文件](./chapters/17-multifile.md)
18. [第三方源码包](./chapters/18-packages.md)

### 第四部分：实用语言能力

19. [泛型](./chapters/19-generics.md)
20. [集合](./chapters/20-collections.md)
21. [Lambda](./chapters/21-lambda.md)
22. [异常与资源](./chapters/22-exceptions.md)
23. [文件](./chapters/23-files.md)
24. [async / 线程](./chapters/24-async.md)（限制章）
25. [FFI](./chapters/25-ffi.md)（限制章）

### 第五部分：开发实践

26. [VS Code](./chapters/26-vscode-ide.md)
27. [调试](./chapters/27-debug.md)
28. [测试与复现](./chapters/28-testing.md)
29. [GC 与资源](./chapters/29-gc-memory.md)
30. [综合项目与后续](./chapters/30-capstone.md)

## 贯穿项目：命令行任务管理器

输入方式：**命令行参数**（已验证）。无交互式 stdin 主线。  
单进程串联请用各阶段的 `demo`；跨进程请用 `save`/`load`。

详见第 30 章与 `examples/tutorial/task-manager/`。

## 练习与答案

- [exercises/](./exercises/README)
- [answers/](./answers/)
