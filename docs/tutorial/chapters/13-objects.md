# 第 13 章：class、对象、字段及构造器

## 本章目标

掌握：`new` 创建对象；字段在实例上；`=` 复制的是引用。`==` 比较引用不是字段值。

## 前置知识

第 1–12 章；JNative 0.32；示例在 `examples/tutorial/`。

## 概念与直观例子

观察 `q.x=10` 后 `p.x` 也变。

## 完整可运行代码

文件：`examples/tutorial/13-objects/Main.jn`（整文件）

```jn
public class Main {
    public static void main(String[] args) {
        Point p = new Point(3, 4);
        System.out.println(String.valueOf(p.manhattan()));
        Point q = p;
        q.x = 10;
        /* q 与 p 指向同一对象 */
        System.out.println(String.valueOf(p.x));
        Point r = new Point(10, 4);
        int sameRef = 0;
        if (p == q) {
            sameRef = 1;
        }
        int sameVal = 0;
        if (p.x == r.x) {
            if (p.y == r.y) {
                sameVal = 1;
            }
        }
        System.out.println(String.valueOf(sameRef));
        System.out.println(String.valueOf(sameVal));
    }
}

class Point {
    int x;
    int y;

    Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    int manhattan() {
        int ax = this.x;
        int ay = this.y;
        if (ax < 0) {
            ax = 0 - ax;
        }
        if (ay < 0) {
            ay = 0 - ay;
        }
        return ax + ay;
    }
}
```

## 文件名及运行命令

```bash
java -jar target/jnative.jar examples/tutorial/13-objects/Main.jn -o /tmp/13-objects -O0
/tmp/13-objects
```

## 精确预期输出

```text
7
10
1
1
```

## 代码关键部分解释

结合源码注释阅读。记住：教程以示例目录为单一事实来源，勿只抄文档中过期片段。

## 常见错误及实际诊断

- 编译失败先看 `file:line:col: error:`。
- 不要用 Java SE Javadoc 推断 JNative 行为。
- 多文件需 `jnative.project`，不是随便 javac。

## 练习

为 Point 增加 `translate` 方法。

## 参考答案或可运行解法

优先对照 `examples/tutorial/` 中最接近的示例改写。综合练习见第 30 章各 stage。

## 本章完成检查

- [ ] 能向别人用自己的话解释本章概念
- [ ] 相关示例已在本机编译运行（若有）

## 下一章

[第 14 章](./14-access.md)
