# 第 14 章：封装与访问控制

## 本章目标

掌握：`private` 字段只能在类内访问；用方法暴露受控操作。

## 前置知识

第 1–13 章；JNative 0.32；示例在 `examples/tutorial/`。

## 概念与直观例子

试着在 Main 里读 `c.n` 应失败。

## 完整可运行代码

文件：`examples/tutorial/14-access/Main.jn`（整文件）

```jn
public class Main {
    public static void main(String[] args) {
        Counter c = new Counter();
        c.bump();
        c.bump();
        System.out.println(String.valueOf(c.get()));
    }
}

class Counter {
    private int n;

    Counter() {
        n = 0;
    }

    void bump() {
        n = n + 1;
    }

    int get() {
        return n;
    }
}
```

## 文件名及运行命令

```bash
java -jar target/jnative.jar examples/tutorial/14-access/Main.jn -o /tmp/14-access -O0
/tmp/14-access
```

## 精确预期输出

```text
2
```

## 代码关键部分解释

结合源码注释阅读。记住：教程以示例目录为单一事实来源，勿只抄文档中过期片段。

## 常见错误及实际诊断

- 编译失败先看 `file:line:col: error:`。
- 不要用 Java SE Javadoc 推断 JNative 行为。
- 多文件需 `jnative.project`，不是随便 javac。

## 练习

增加 `reset()` 方法。

## 参考答案或可运行解法

优先对照 `examples/tutorial/` 中最接近的示例改写。综合练习见第 30 章各 stage。

## 本章完成检查

- [ ] 能向别人用自己的话解释本章概念
- [ ] 相关示例已在本机编译运行（若有）

## 下一章

[第 15 章](./15-inherit.md)
