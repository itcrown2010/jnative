# 第 19 章：泛型及当前支持边界

## 本章目标

掌握：类/方法可用类型参数；集合常配合泛型。通配符 `?` 为实验性。

## 前置知识

第 1–18 章；JNative 0.32；示例在 `examples/tutorial/`。

## 概念与直观例子

primitive 特化如 `Box<int>` 可用（已验证）。

## 完整可运行代码

文件：`examples/tutorial/19-generics/Main.jn`（整文件）

```jn
public class Main {
    public static void main(String[] args) {
        Box<String> b = new Box<String>();
        b.set("hi");
        System.out.println(b.get());
        Box<int> n = new Box<int>();
        n.set(42);
        System.out.println(String.valueOf(n.get()));
    }
}

class Box<T> {
    T value;

    void set(T v) {
        value = v;
    }

    T get() {
        return value;
    }
}
```

## 文件名及运行命令

```bash
java -jar target/jnative.jar examples/tutorial/19-generics/Main.jn -o /tmp/19-generics -O0
/tmp/19-generics
```

## 精确预期输出

```text
hi
42
```

## 代码关键部分解释

结合源码注释阅读。记住：教程以示例目录为单一事实来源，勿只抄文档中过期片段。

## 常见错误及实际诊断

- 编译失败先看 `file:line:col: error:`。
- 不要用 Java SE Javadoc 推断 JNative 行为。
- 多文件需 `jnative.project`，不是随便 javac。

## 练习

写 `Box<boolean>`。

## 参考答案或可运行解法

优先对照 `examples/tutorial/` 中最接近的示例改写。综合练习见第 30 章各 stage。

## 本章完成检查

- [ ] 能向别人用自己的话解释本章概念
- [ ] 相关示例已在本机编译运行（若有）

## 下一章

[第 20 章](./20-collections.md)
