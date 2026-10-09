# 泛型及限制

## 概念

标准库大量使用泛型接口（如 `List<T>`、`Map<K,V>`、`Iterator<T>`）。编译器支持泛型的 **子集**。

## 最小示例

使用集合 API（需链接 stdlib，项目构建即可）：

```java
public class GenDemo {
    public static void main(String[] args) {
        ArrayList<String> list = new ArrayList<String>();
        list.add("a");
        System.out.println(list.size());
    }
}
```

> 请用 `jnative build` 在项目中验证；若单文件缺省 stdlib 注入方式与项目不同，以项目模式为准。

## 明确语义

- 类型擦除式实现细节与 JVM 不同；不要依赖反射读取泛型实参。
- 通配符、边界的完整集合可能不齐。

## 常见错误

- 抄写 Java SE 的复杂泛型签名导致编译失败。
- 在 WASM 目标使用未验证的泛型密集代码而不跑测试。

## 支持限制

高级泛型（捕获转换、复杂通配符推断）：**实验性 / 未验证**。见 stdlib `CollectionsLib.java` 与 e2e 测试。

## 相关

- [集合](/language/collections) · [API：集合](/api/collections)
