# 异常、finally 与资源管理

## 概念

支持 `try` / `catch` / `finally` 子集；stdlib 含 `AutoCloseable` 与资源跟踪相关类型（见 `CollectionsLib.java` 开头）。

## 最小示例

```java
public class ExcDemo {
    public static void main(String[] args) {
        try {
            int z = 1 / 1;
            System.out.println(z);
        } catch (Exception e) {
            System.out.println("err");
        } finally {
            System.out.println("done");
        }
    }
}
```

请本机编译确认 `catch` 类型是否被接受；若失败，收窄为当前实现允许的类型。

## 明确语义

- 异常类层次不等于 OpenJDK。
- 资源释放责任：谁打开谁关闭；UI/句柄见平台指南。

## 常见错误

- 空 `catch` 吞掉错误导致 UI「无响应」。
- 假设 try-with-resources 全语法可用而不验证。

## 支持限制

受检异常强制、完整 `Throwable` 链：**实验性 / 未验证**。

## 相关

- [API：IO](/api/io) · 调试章「异常展示」
