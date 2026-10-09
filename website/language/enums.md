# 枚举与 final

## 概念

`enum` 与 `final` 字段/变量在语言子集中有支持路径；具体能力以编译器与测试为准。

## 最小示例

先写最小 `enum` 并编译；若当前工具链报错，视为该构造在你目标上 **未支持**，改用 `int` 常量或类封装——**不要**从本文复制未验证的复杂 enum 方法体。

```java
// 请在本机编译验证后再用于业务
public class ColorBox {
    public static final int RED = 1;
    public static final int BLUE = 2;

    public static void main(String[] args) {
        System.out.println(RED + BLUE);
    }
}
```

## 支持限制

复杂 enum（抽象方法、switch 穷尽性）可能不完整。状态：**实验性 / 待你本地验证**。通过后可把用例贡献回文档。

## 相关

- [限制与路线](/language/limits)
