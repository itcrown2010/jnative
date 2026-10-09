# 条件、循环与 switch

## 概念

`if` / `else`、`while`、`for`、`break`/`continue`，以及 `switch`（能力以编译器子集为准）。

## 最小示例

```java
public class LoopDemo {
    public static void main(String[] args) {
        int sum = 0;
        int i = 0;
        while (i < 5) {
            sum = sum + i;
            i = i + 1;
        }
        System.out.println(sum);
    }
}
```

## 明确语义

- 条件表达式需为布尔语义（与实现检查一致）。
- 增强 for（`for (T x : coll)`）依赖集合/迭代器支持，见 [集合](/language/collections)。

## 常见错误

- 使用未实现的 switch 模式匹配 / 箭头语法而不查限制页。

## 支持限制

现代 Java 的 switch 表达式、模式匹配：**未支持** 或未验证——以编译错误为准，勿抄 SE 17+ 示例。

## 相关

- 仓库内控制流相关 parser/semantic 测试（维护者）
