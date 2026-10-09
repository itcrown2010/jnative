# 数值运算、转换、精度与边界

## 概念

支持常见算术与比较。优化器（`-O0`…`-O3`）可能折叠常量；调试时用 `-O0`/`--debug`。

## 最小示例

```java
public class MathDemo {
    public static void main(String[] args) {
        int n = 1 + 1;
        System.out.println(n);
    }
}
```

模板测试 `MathTest.jn` 使用 `1/ok` 形式断言 `1+1==2`。

## 明确语义

- 整数除零等行为以实现为准；不要用文档猜测硬件陷阱指令细节。
- `float`/`double` 精度与 NaN 比较：先写最小程序验证再用于业务。

## 常见错误

- 把「通过编译」当成「与 Java SE 位级一致」。
- 在 WASM 目标假设与原生相同的整数宽度/溢出表现而不做测试。

## 支持限制

BigInteger/完整 `Math` 库：见 stdlib `math` 模块提取结果；未列出的方法视为 **未验证**。

## 相关

- [API：数值与集合](/api/collections) · `src/main/resources/stdlib/math/`
