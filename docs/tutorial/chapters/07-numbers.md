# 第 07 章：整数、浮点、布尔及数值边界

## 本章目标

掌握：整数除法截断；溢出按补码回绕（工具链假定，专用套件有限）；浮点不宜表示金额。

## 前置知识

第 1–6 章；JNative 0.32；示例在 `examples/tutorial/`。

## 概念与直观例子

看到 `long-ok` 与溢出后的负数。

## 完整可运行代码

文件：`examples/tutorial/07-numbers/Main.jn`（整文件）

```jn
public class Main {
    public static void main(String[] args) {
        int a = 7;
        int b = 3;
        System.out.println(String.valueOf(a + b));
        System.out.println(String.valueOf(a / b));
        System.out.println(String.valueOf(a % b));
        long big = 10000000000L;
        long twice = big + big;
        if (twice > big) {
            System.out.println("long-ok");
        }
        double x = 0.1;
        double y = 0.2;
        double z = x + y;
        /* 浮点不可精确表示 0.3；金额勿用 double */
        if (z > 0.29) {
            if (z < 0.31) {
                System.out.println("near");
            }
        }
        int wrap = 2147483647;
        wrap = wrap + 1;
        System.out.println(String.valueOf(wrap));
    }
}
```

## 文件名及运行命令

```bash
java -jar target/jnative.jar examples/tutorial/07-numbers/Main.jn -o /tmp/07-numbers -O0
/tmp/07-numbers
```

## 精确预期输出

```text
10
2
1
long-ok
near
-2147483648
```

## 代码关键部分解释

结合源码注释阅读。记住：教程以示例目录为单一事实来源，勿只抄文档中过期片段。

## 常见错误及实际诊断

- 编译失败先看 `file:line:col: error:`。
- 不要用 Java SE Javadoc 推断 JNative 行为。
- 多文件需 `jnative.project`，不是随便 javac。

## 练习

把 `a / b` 改成需要余数的场景并打印 `%`。

## 参考答案或可运行解法

优先对照 `examples/tutorial/` 中最接近的示例改写。综合练习见第 30 章各 stage。

## 本章完成检查

- [ ] 能向别人用自己的话解释本章概念
- [ ] 相关示例已在本机编译运行（若有）

## 下一章

[第 08 章](./08-strings.md)
