# 第 09 章：运算符、类型转换和求值顺序

## 本章目标

掌握：支持算术、比较、逻辑、位运算与移位；`(int)` 等原始转换；三元运算。

## 前置知识

第 1–8 章；JNative 0.32；示例在 `examples/tutorial/`。

## 概念与直观例子

`&&`/`||` 短路；`&`/`|` 对 boolean 不短路（与 Java 类似点见语言档案）。

## 完整可运行代码

文件：`examples/tutorial/09-ops/Main.jn`（整文件）

```jn
public class Main {
    public static void main(String[] args) {
        int a = 5;
        int b = 2;
        System.out.println(String.valueOf(a << 1));
        System.out.println(String.valueOf(a & b));
        boolean p = true;
        boolean q = false;
        if (p && !q) {
            System.out.println("logic");
        }
        double d = 3.9;
        int n = (int) d;
        System.out.println(String.valueOf(n));
        int pick = p ? 1 : 0;
        System.out.println(String.valueOf(pick));
    }
}
```

## 文件名及运行命令

```bash
java -jar target/jnative.jar examples/tutorial/09-ops/Main.jn -o /tmp/09-ops -O0
/tmp/09-ops
```

## 精确预期输出

```text
10
0
logic
3
1
```

## 代码关键部分解释

结合源码注释阅读。记住：教程以示例目录为单一事实来源，勿只抄文档中过期片段。

## 常见错误及实际诊断

- 编译失败先看 `file:line:col: error:`。
- 不要用 Java SE Javadoc 推断 JNative 行为。
- 多文件需 `jnative.project`，不是随便 javac。

## 练习

写一个用移位计算 `×8` 的例子。

## 参考答案或可运行解法

优先对照 `examples/tutorial/` 中最接近的示例改写。综合练习见第 30 章各 stage。

## 本章完成检查

- [ ] 能向别人用自己的话解释本章概念
- [ ] 相关示例已在本机编译运行（若有）

## 下一章

[第 10 章](./10-control.md)
