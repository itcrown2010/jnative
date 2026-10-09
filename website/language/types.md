# 基础类型与变量

## 概念

提供 Java 风格原始类型与引用类型子集：`int`、`long`、`boolean`、`byte`、`short`、`char`、`float`、`double`、`String`、数组与类实例等。局部变量需初始化后再使用（与编译器检查一致）。

## 最小可运行示例

```java
public class TypesDemo {
    public static void main(String[] args) {
        int n = 42;
        boolean ok = n > 0;
        String s = "n=";
        System.out.println(s + n);
        System.out.println(ok);
    }
}
```

```bash
jnative TypesDemo.java -o /tmp/TypesDemo && /tmp/TypesDemo
```

## 明确语义

- `String` 为标准库/运行时类型，不是 JVM `java.lang` 完整实现。
- 装箱类型与完整包装类层次 **勿假设** 与 OpenJDK 一致。

## 常见错误

- 使用未实现的引用类型 API。
- 依赖隐式宽化/装箱的边界情况而不做小测试。

## 支持限制

完整反射、`Object` 全方法集、动态代理：**未支持** 或未验证。详见 [限制与路线](/language/limits)。

## 相关

- [数值运算](/language/numbers) · [API：基础](/api/basics) · 模板测试 `examples/guide-hello/tests/IdentityTest.jn`
