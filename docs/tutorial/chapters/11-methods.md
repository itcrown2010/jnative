# 第 11 章：方法、参数、返回值与递归

## 本章目标

掌握：方法可返回值或 `void`；支持递归（注意栈深度）。

## 前置知识

第 1–10 章；JNative 0.32；示例在 `examples/tutorial/`。

## 概念与直观例子

理解形参是局部变量。

## 完整可运行代码

文件：`examples/tutorial/11-methods/Main.jn`（整文件）

```jn
public class Main {
    static int add(int x, int y) {
        return x + y;
    }

    static int fact(int n) {
        if (n <= 1) {
            return 1;
        }
        return n * fact(n - 1);
    }

    public static void main(String[] args) {
        System.out.println(String.valueOf(add(2, 3)));
        System.out.println(String.valueOf(fact(5)));
        greet("Reader");
    }

    static void greet(String name) {
        System.out.println("hi " + name);
    }
}
```

## 文件名及运行命令

```bash
java -jar target/jnative.jar examples/tutorial/11-methods/Main.jn -o /tmp/11-methods -O0
/tmp/11-methods
```

## 精确预期输出

```text
5
120
hi Reader
```

## 代码关键部分解释

结合源码注释阅读。记住：教程以示例目录为单一事实来源，勿只抄文档中过期片段。

## 常见错误及实际诊断

- 编译失败先看 `file:line:col: error:`。
- 不要用 Java SE Javadoc 推断 JNative 行为。
- 多文件需 `jnative.project`，不是随便 javac。

## 练习

写 `sumTo(n)` 递归求和。

## 参考答案或可运行解法

优先对照 `examples/tutorial/` 中最接近的示例改写。综合练习见第 30 章各 stage。

## 本章完成检查

- [ ] 能向别人用自己的话解释本章概念
- [ ] 相关示例已在本机编译运行（若有）

## 下一章

[第 12 章](./12-arrays.md)
