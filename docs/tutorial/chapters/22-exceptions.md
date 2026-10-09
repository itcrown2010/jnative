# 第 22 章：异常、finally 和资源管理

## 本章目标

掌握：`try/catch/finally`；`throw`；try-with-resources 声明形式可用；无 multi-catch；无受检 throws 强制。

## 前置知识

第 1–21 章；JNative 0.32；示例在 `examples/tutorial/`。

## 概念与直观例子

finally 总会走。

## 完整可运行代码

文件：`examples/tutorial/22-exceptions/Main.jn`（整文件）

```jn
public class Main {
    public static void main(String[] args) {
        try {
            mayFail(0);
            System.out.println("ok");
        } catch (Exception e) {
            System.out.println("caught");
        } finally {
            System.out.println("finally");
        }
        try {
            mayFail(1);
            System.out.println("ok2");
        } catch (Exception e) {
            System.out.println("caught2");
        }
    }

    static void mayFail(int mode) {
        if (mode == 0) {
            throw new RuntimeException("boom");
        }
    }
}
```

## 文件名及运行命令

```bash
java -jar target/jnative.jar examples/tutorial/22-exceptions/Main.jn -o /tmp/22-exceptions -O0
/tmp/22-exceptions
```

## 精确预期输出

```text
caught
finally
ok2
```

## 代码关键部分解释

结合源码注释阅读。记住：教程以示例目录为单一事实来源，勿只抄文档中过期片段。

## 常见错误及实际诊断

- 编译失败先看 `file:line:col: error:`。
- 不要用 Java SE Javadoc 推断 JNative 行为。
- 多文件需 `jnative.project`，不是随便 javac。

## 练习

在 catch 中打印后重新抛出（观察行为）。

## 参考答案或可运行解法

优先对照 `examples/tutorial/` 中最接近的示例改写。综合练习见第 30 章各 stage。

## 本章完成检查

- [ ] 能向别人用自己的话解释本章概念
- [ ] 相关示例已在本机编译运行（若有）

## 下一章

[第 23 章](./23-files.md)
