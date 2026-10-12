# 第 15 章：继承、接口及动态分派

## 本章目标

掌握：`extends` 单继承；`implements` 接口；调用看运行时类型。无默认接口方法。

## 前置知识

第 1–14 章；JNative 0.32；示例在 `examples/tutorial/`。

## 概念与直观例子

把变量类型写成 Animal 仍打印 meow。

## 完整可运行代码

文件：`examples/tutorial/15-inherit/Main.jn`（整文件）

```jn
public class Main {
    public static void main(String[] args) {
        Animal a = new Cat();
        System.out.println(a.speak());
        Greeter g = new HelloGreeter();
        System.out.println(g.greet("Ada"));
    }
}

class Animal {
    String speak() {
        return "…";
    }
}

class Cat extends Animal {
    String speak() {
        return "meow";
    }
}

interface Greeter {
    String greet(String name);
}

class HelloGreeter implements Greeter {
    public String greet(String name) {
        return "hello " + name;
    }
}
```

## 文件名及运行命令

```bash
java -jar target/jnative.jar examples/tutorial/15-inherit/Main.jn -o /tmp/15-inherit -O0
/tmp/15-inherit
```

## 精确预期输出

```text
meow
hello Ada
```

## 代码关键部分解释

结合源码注释阅读。记住：教程以示例目录为单一事实来源，勿只抄文档中过期片段。

## 常见错误及实际诊断

- 编译失败先看 `file:line:col: error:`。
- 不要用 Java SE Javadoc 推断 JNative 行为。
- 多文件需 `jnative.project`，不是随便 javac。

## 练习

再增加 Dog 子类。

## 参考答案或可运行解法

优先对照 `examples/tutorial/` 中最接近的示例改写。综合练习见第 30 章各 stage。

## 本章完成检查

- [ ] 能向别人用自己的话解释本章概念
- [ ] 相关示例已在本机编译运行（若有）

## 下一章

[第 16 章](./16-enum-final.md)
