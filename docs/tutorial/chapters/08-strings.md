# 第 08 章：字符串、Unicode 与编码

## 本章目标

掌握：`length`/`charAt`/`substring`/`indexOf` 按 **UTF-16 码元**；`split` 是字面分隔符不是正则。

## 前置知识

第 1–7 章；JNative 0.32；示例在 `examples/tutorial/`。

## 概念与直观例子

打印时避免把 `char` 直接 `String.valueOf` 成整数码点观感。

## 完整可运行代码

文件：`examples/tutorial/08-strings/Main.jn`（整文件）

```jn
public class Main {
    public static void main(String[] args) {
        String s = "JNative";
        System.out.println(String.valueOf(s.length()));
        /* charAt 返回 char；打印时用拼接避免当成整数 */
        System.out.println("ch=" + s.substring(0, 1));
        System.out.println(s.substring(1, 4));
        System.out.println(String.valueOf(s.indexOf("Nat")));
        String t = "JN" + "ative";
        if (s.compareTo(t) == 0) {
            System.out.println("equal");
        }
        /* length = UTF-16 码元个数 */
        String smile = "A";
        System.out.println(String.valueOf(smile.length()));
    }
}
```

## 文件名及运行命令

```bash
java -jar target/jnative.jar examples/tutorial/08-strings/Main.jn -o /tmp/08-strings -O0
/tmp/08-strings
```

## 精确预期输出

```text
7
ch=J
Nat
1
equal
1
```

## 代码关键部分解释

结合源码注释阅读。记住：教程以示例目录为单一事实来源，勿只抄文档中过期片段。

## 常见错误及实际诊断

- 编译失败先看 `file:line:col: error:`。
- 不要用 Java SE Javadoc 推断 JNative 行为。
- 多文件需 `jnative.project`，不是随便 javac。

## 练习

用 `substring` 取出姓/名并拼接。

## 参考答案或可运行解法

优先对照 `examples/tutorial/` 中最接近的示例改写。综合练习见第 30 章各 stage。

## 本章完成检查

- [ ] 能向别人用自己的话解释本章概念
- [ ] 相关示例已在本机编译运行（若有）

## 下一章

[第 09 章](./09-operators.md)
