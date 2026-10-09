# class、继承、接口与构造器

## 概念

支持类、字段、方法、构造器，以及继承/接口的 **子集**（用于 stdlib 与示例中的 UI、集合等）。

## 最小示例

```java
public class Point {
    public int x;
    public int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int manhattan() {
        int ax = this.x;
        int ay = this.y;
        if (ax < 0) { ax = 0 - ax; }
        if (ay < 0) { ay = 0 - ay; }
        return ax + ay;
    }

    public static void main(String[] args) {
        Point p = new Point(3, 4);
        System.out.println(p.manhattan());
    }
}
```

## 明确语义

- `this` 赋值与字段访问在示例与测试中广泛使用。
- 接口可作为 SAM 目标配合 lambda（见 [Lambda](/language/lambdas)）。

## 常见错误

- 依赖多重继承、内部类、匿名类全套 SE 语义。
- 假设 `synchronized` 方法与 JVM 内存模型一致。

## 支持限制

| 项 | 状态 |
|----|------|
| 单继承类层次 | 实验性 |
| 接口 | 实验性 |
| 内部类/匿名类 | 未验证 / 部分可能未支持 |
| 反射创建 | 未支持 |

## 相关

- [枚举](/language/enums) · UI/集合 stdlib 源码
