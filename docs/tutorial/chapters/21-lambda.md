# 第 21 章：Lambda、闭包和方法引用

## 本章目标

掌握：SAM 接口可写 lambda；可捕获 effectively final 局部变量；支持 `::`。wasm32-browser **拒绝** callable。

## 前置知识

第 1–20 章；JNative 0.32；示例在 `examples/tutorial/`。

## 概念与直观例子

闭包不要在对象销毁后继续用。

## 完整可运行代码

文件：`examples/tutorial/21-lambda/Main.jn`（整文件）

```jn
public class Main {
    public static void main(String[] args) {
        IntOp twice = (n) -> n * 2;
        System.out.println(String.valueOf(twice.apply(5)));
        final int base = 10;
        IntOp addBase = (n) -> n + base;
        System.out.println(String.valueOf(addBase.apply(3)));
        IntOp viaRef = Main::inc;
        System.out.println(String.valueOf(viaRef.apply(7)));
    }

    static int inc(int n) {
        return n + 1;
    }
}

interface IntOp {
    int apply(int n);
}
```

## 文件名及运行命令

```bash
java -jar target/jnative.jar examples/tutorial/21-lambda/Main.jn -o /tmp/21-lambda -O0
/tmp/21-lambda
```

## 精确预期输出

```text
10
13
8
```

## 代码关键部分解释

结合源码注释阅读。记住：教程以示例目录为单一事实来源，勿只抄文档中过期片段。

## 常见错误及实际诊断

- 编译失败先看 `file:line:col: error:`。
- 不要用 Java SE Javadoc 推断 JNative 行为。
- 多文件需 `jnative.project`，不是随便 javac。

## 练习

写 `IntOp` 计算平方。

## 参考答案或可运行解法

优先对照 `examples/tutorial/` 中最接近的示例改写。综合练习见第 30 章各 stage。

## 本章完成检查

- [ ] 能向别人用自己的话解释本章概念
- [ ] 相关示例已在本机编译运行（若有）

## 下一章

[第 22 章](./22-exceptions.md)
