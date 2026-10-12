# 第 20 章：集合

## 本章目标

掌握：`ArrayList`/`HashMap` 等；按索引删除用 `removeAt`。非线程安全。

## 前置知识

第 1–19 章；JNative 0.32；示例在 `examples/tutorial/`。

## 概念与直观例子

遍历用索引 while 或迭代器。

## 完整可运行代码

文件：`examples/tutorial/20-collections/Main.jn`（整文件）

```jn
public class Main {
    public static void main(String[] args) {
        ArrayList<String> xs = new ArrayList<String>();
        xs.add("tea");
        xs.add("code");
        System.out.println(String.valueOf(xs.size()));
        System.out.println(xs.get(0));
        int i = 0;
        while (i < xs.size()) {
            System.out.println(xs.get(i));
            i = i + 1;
        }
        HashMap<String, int> ages = new HashMap<String, int>();
        ages.put("Ada", 36);
        System.out.println(String.valueOf(ages.get("Ada")));
    }
}
```

## 文件名及运行命令

```bash
java -jar target/jnative.jar examples/tutorial/20-collections/Main.jn -o /tmp/20-collections -O0
/tmp/20-collections
```

## 精确预期输出

```text
2
tea
tea
code
36
```

## 代码关键部分解释

结合源码注释阅读。记住：教程以示例目录为单一事实来源，勿只抄文档中过期片段。

## 常见错误及实际诊断

- 编译失败先看 `file:line:col: error:`。
- 不要用 Java SE Javadoc 推断 JNative 行为。
- 多文件需 `jnative.project`，不是随便 javac。

## 练习

用 ArrayList 存 3 个名字并打印。

## 参考答案或可运行解法

优先对照 `examples/tutorial/` 中最接近的示例改写。综合练习见第 30 章各 stage。

## 本章完成检查

- [ ] 能向别人用自己的话解释本章概念
- [ ] 相关示例已在本机编译运行（若有）

## 下一章

[第 21 章](./21-lambda.md)
