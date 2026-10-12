# 第 10 章：if、循环、switch

## 本章目标

掌握：经典 `if`/`while`/`for`/`switch`；`break`/`continue`；无标签 break；无模式 switch。

## 前置知识

第 1–9 章；JNative 0.32；示例在 `examples/tutorial/`。

## 概念与直观例子

对照预期输出理解 `continue` 跳过了 `f2`。

## 完整可运行代码

文件：`examples/tutorial/10-control/Main.jn`（整文件）

```jn
public class Main {
    public static void main(String[] args) {
        int n = 3;
        if (n > 5) {
            System.out.println("big");
        } else if (n > 0) {
            System.out.println("mid");
        } else {
            System.out.println("small");
        }
        int i = 0;
        int sum = 0;
        while (i < 4) {
            sum = sum + i;
            i = i + 1;
        }
        System.out.println(String.valueOf(sum));
        int j = 0;
        for (j = 0; j < 5; j = j + 1) {
            if (j == 2) {
                continue;
            }
            if (j == 4) {
                break;
            }
            System.out.println("f" + String.valueOf(j));
        }
        int code = 2;
        switch (code) {
            case 1:
                System.out.println("one");
                break;
            case 2:
                System.out.println("two");
                break;
            default:
                System.out.println("other");
                break;
        }
    }
}
```

## 文件名及运行命令

```bash
java -jar target/jnative.jar examples/tutorial/10-control/Main.jn -o /tmp/10-control -O0
/tmp/10-control
```

## 精确预期输出

```text
mid
6
f0
f1
f3
two
```

## 代码关键部分解释

结合源码注释阅读。记住：教程以示例目录为单一事实来源，勿只抄文档中过期片段。

## 常见错误及实际诊断

- 编译失败先看 `file:line:col: error:`。
- 不要用 Java SE Javadoc 推断 JNative 行为。
- 多文件需 `jnative.project`，不是随便 javac。

## 练习

用 `switch` 打印 1–3 的英文数字。

## 参考答案或可运行解法

优先对照 `examples/tutorial/` 中最接近的示例改写。综合练习见第 30 章各 stage。

## 本章完成检查

- [ ] 能向别人用自己的话解释本章概念
- [ ] 相关示例已在本机编译运行（若有）

## 下一章

[第 11 章](./11-methods.md)
