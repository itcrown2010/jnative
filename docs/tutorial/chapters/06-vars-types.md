# 第 06 章：变量、类型、赋值及作用域

## 本章目标

掌握：变量有类型与作用域；使用前必须赋值（定值分析）。

## 前置知识

第 1–5 章；JNative 0.32；示例在 `examples/tutorial/`。

## 概念与直观例子

局部变量在块结束后不可见；赋值改的是变量里的值（对象则是引用，见第13章）。

## 完整可运行代码

文件：`examples/tutorial/06-vars/Main.jn`（整文件）

```jn
public class Main {
    public static void main(String[] args) {
        int score = 100;
        boolean ok = true;
        String name = "Ada";
        System.out.println(name);
        System.out.println(String.valueOf(score));
        if (ok) {
            System.out.println("ready");
        }
        {
            int inner = 7;
            System.out.println(String.valueOf(inner));
        }
        /* inner 在此不可见 */
        score = score + 1;
        System.out.println(String.valueOf(score));
    }
}
```

## 文件名及运行命令

```bash
java -jar target/jnative.jar examples/tutorial/06-vars/Main.jn -o /tmp/06-vars -O0
/tmp/06-vars
```

## 精确预期输出

```text
Ada
100
ready
7
101
```

## 代码关键部分解释

结合源码注释阅读。记住：教程以示例目录为单一事实来源，勿只抄文档中过期片段。

## 常见错误及实际诊断

- 编译失败先看 `file:line:col: error:`。
- 不要用 Java SE Javadoc 推断 JNative 行为。
- 多文件需 `jnative.project`，不是随便 javac。

## 练习

在块内声明变量，确认块外无法使用（应编译失败）。

## 参考答案或可运行解法

优先对照 `examples/tutorial/` 中最接近的示例改写。综合练习见第 30 章各 stage。

## 本章完成检查

- [ ] 能向别人用自己的话解释本章概念
- [ ] 相关示例已在本机编译运行（若有）

## 下一章

[第 07 章](./07-numbers.md)
