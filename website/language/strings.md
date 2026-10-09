# 字符串、Unicode 与编码

## 概念

`String` 字面量、`+` 拼接、`length`、`compareTo` 等在入门路径中可用（见模板 `StringTest.jn`）。

## 最小示例

```java
public class StrDemo {
    public static void main(String[] args) {
        String a = "JNative";
        String b = "J" + "Native";
        System.out.println(a.compareTo(b));
    }
}
```

## 明确语义

- 拼接与比较的可用方法以 stdlib/编译器实现为准。
- 源文件建议 UTF-8。

## 常见错误

- 假设完整 `java.lang.String` / `Charset` / `codePoint` API。
- 在 WASM 与原生之间混用未验证的编码转换。

## 支持限制

正则、完整 Unicode 规范化、locale 敏感排序：多数 **未验证** 或 **未支持**。查 [API：基础](/api/basics) 提取列表。

## 相关

- `examples/guide-hello/tests/StringTest.jn`
