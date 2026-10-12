# 第 12 章：数组及增强 for

## 本章目标

掌握：`new int[n]`；`.length`；增强 for 可用于数组。

## 前置知识

第 1–11 章；JNative 0.32；示例在 `examples/tutorial/`。

## 概念与直观例子

下标越界会失败——先检查长度。

## 完整可运行代码

文件：`examples/tutorial/12-arrays/Main.jn`（整文件）

```jn
public class Main {
    public static void main(String[] args) {
        int[] xs = new int[4];
        xs[0] = 1;
        xs[1] = 2;
        xs[2] = 3;
        xs[3] = 4;
        System.out.println(String.valueOf(xs.length));
        int sum = 0;
        int i = 0;
        while (i < xs.length) {
            sum = sum + xs[i];
            i = i + 1;
        }
        System.out.println(String.valueOf(sum));
        int sum2 = 0;
        for (int v : xs) {
            sum2 = sum2 + v;
        }
        System.out.println(String.valueOf(sum2));
        String[] names = new String[2];
        names[0] = "a";
        names[1] = "b";
        System.out.println(names[0] + names[1]);
    }
}
```

## 文件名及运行命令

```bash
java -jar target/jnative.jar examples/tutorial/12-arrays/Main.jn -o /tmp/12-arrays -O0
/tmp/12-arrays
```

## 精确预期输出

```text
4
10
10
ab
```

## 代码关键部分解释

结合源码注释阅读。记住：教程以示例目录为单一事实来源，勿只抄文档中过期片段。

## 常见错误及实际诊断

- 编译失败先看 `file:line:col: error:`。
- 不要用 Java SE Javadoc 推断 JNative 行为。
- 多文件需 `jnative.project`，不是随便 javac。

## 练习

反转数组并打印。

## 参考答案或可运行解法

优先对照 `examples/tutorial/` 中最接近的示例改写。综合练习见第 30 章各 stage。

## 本章完成检查

- [ ] 能向别人用自己的话解释本章概念
- [ ] 相关示例已在本机编译运行（若有）

## 下一章

[第 13 章](./13-objects.md)
