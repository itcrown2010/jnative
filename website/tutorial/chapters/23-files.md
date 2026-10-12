# 第 23 章：文件读写

## 本章目标

掌握：`File.writeString` / `readString` / `exists`。GC **不**代替你关闭更复杂的流资源。

## 前置知识

第 1–22 章；JNative 0.32；示例在 `examples/tutorial/`。

## 概念与直观例子

路径相对当前工作目录。

## 完整可运行代码

文件：`examples/tutorial/23-files/Main.jn`（整文件）

```jn
public class Main {
    public static void main(String[] args) {
        String path = "build/tutorial-files-demo.txt";
        File.writeString(path, "line1\nline2");
        String text = File.readString(path);
        System.out.println(text);
        if (File.exists(path)) {
            System.out.println("exists");
        }
    }
}
```

## 文件名及运行命令

```bash
java -jar target/jnative.jar examples/tutorial/23-files/Main.jn -o /tmp/23-files -O0
/tmp/23-files
```

## 精确预期输出

```text
line1
line2
exists
```

## 代码关键部分解释

结合源码注释阅读。记住：教程以示例目录为单一事实来源，勿只抄文档中过期片段。

## 常见错误及实际诊断

- 编译失败先看 `file:line:col: error:`。
- 不要用 Java SE Javadoc 推断 JNative 行为。
- 多文件需 `jnative.project`，不是随便 javac。

## 练习

写文件后判断 exists。

## 参考答案或可运行解法

优先对照 `examples/tutorial/` 中最接近的示例改写。综合练习见第 30 章各 stage。

## 本章完成检查

- [ ] 能向别人用自己的话解释本章概念
- [ ] 相关示例已在本机编译运行（若有）

## 下一章

[第 24 章](./24-async.md)
