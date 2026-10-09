# 第 16 章：enum、final

## 本章目标

掌握：`enum` 基本常量可用；`final` 局部/参数/类/方法可用；**final 字段当前不支持**（硬错误）。

## 前置知识

第 1–15 章；JNative 0.32；示例在 `examples/tutorial/`。

## 概念与直观例子

枚举名避免与 stdlib 冲突（如 Color）。

## 完整可运行代码

文件：`examples/tutorial/16-enum-final/Main.jn`（整文件）

```jn
public class Main {
    public static void main(String[] args) {
        final int limit = 3;
        InkTone t = InkTone.DARK;
        System.out.println(t.name());
        System.out.println(String.valueOf(t.ordinal()));
        int code = 0;
        if (t == InkTone.DARK) {
            code = 1;
        }
        System.out.println(String.valueOf(code));
        System.out.println(String.valueOf(limit));
        /* final 局部变量不可再赋值；final 字段当前语言不支持 */
    }
}

enum InkTone {
    LIGHT,
    DARK,
    ACCENT
}
```

## 文件名及运行命令

```bash
java -jar target/jnative.jar examples/tutorial/16-enum-final/Main.jn -o /tmp/16-enum-final -O0
/tmp/16-enum-final
```

## 精确预期输出

```text
DARK
1
1
3
```

## 代码关键部分解释

结合源码注释阅读。记住：教程以示例目录为单一事实来源，勿只抄文档中过期片段。

## 常见错误及实际诊断

- 编译失败先看 `file:line:col: error:`。
- 不要用 Java SE Javadoc 推断 JNative 行为。
- 多文件需 `jnative.project`，不是随便 javac。

## 练习

用 switch 处理 InkTone。

## 参考答案或可运行解法

优先对照 `examples/tutorial/` 中最接近的示例改写。综合练习见第 30 章各 stage。

## 本章完成检查

- [ ] 能向别人用自己的话解释本章概念
- [ ] 相关示例已在本机编译运行（若有）

## 下一章

[第 17 章](./17-multifile.md)
