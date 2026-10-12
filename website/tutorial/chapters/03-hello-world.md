# 第 03 章：Hello World

## 本章目标

写出、编译并运行第一个 JNative 程序，确认工具链可用。

## 前置知识

第 01–02 章；仓库根存在 `target/jnative.jar`。

## 概念与直观例子

程序从 `public static void main(String[] args)` 开始执行。`System.out.println` 向标准输出打印一行文本。

## 完整可运行代码

**替换完整文件** `examples/tutorial/01-hello/Main.jn`：

```jn
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, JNative!");
    }
}
```

## 文件名及运行命令

```bash
java -jar target/jnative.jar examples/tutorial/01-hello/Main.jn -o /tmp/01-hello -O0
/tmp/01-hello
```

## 精确预期输出

```text
Hello, JNative!
```

## 代码关键部分解释

- `Main`：公共类名；单文件示例中与文件名习惯一致即可。
- `args`：命令行参数数组；本程序未使用。
- `-O0`：关闭优化，便于后续对照调试。

## 常见错误及实际诊断

| 症状 | 处理 |
|------|------|
| Unable to access jarfile | 在仓库根执行，或先 `mvn -DskipTests package` |
| 无输出 | 确认运行的是新生成的二进制路径 |

## 练习

1. 把问候语改成自己的名字并运行。  
2. 再打印第二行 `Welcome`。

## 参考答案或可运行解法

```jn
System.out.println("Hello, Ada!");
System.out.println("Welcome");
```

## 本章完成检查

- [ ] 本机输出与预期一致（或按练习修改后可解释差异）

## 下一章

[第 04 章：项目目录、入口方法和输出](./04-项目与入口.md)
