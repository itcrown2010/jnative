# 集合与遍历

## 概念

`src/main/resources/stdlib/collections/CollectionsLib.java` 提供 `ArrayList`、`HashMap`、`HashSet`、`ArrayDeque`、`StringBuilder`、`Iterator` 等。

## 最小示例

```java
public class ListDemo {
    public static void main(String[] args) {
        ArrayList<String> xs = new ArrayList<String>();
        xs.add("one");
        xs.add("two");
        int i = 0;
        while (i < xs.size()) {
            System.out.println(xs.get(i));
            i = i + 1;
        }
    }
}
```

## 明确语义

- 接口层次：`Collection` / `List` / `Map` 等见源码。
- 遍历优先用索引或 `Iterator`；增强 for 取决于实现。

## 常见错误

- 假设 `Collections.synchronized*` 或完整 Java Collections Framework。
- 在 UI 线程外修改列表却在 UI 线程读取而不做同步约定（见 GC/线程章）。

## 支持限制

并发集合、`WeakHashMap` 等：**未验证**。线程池类存在于同一文件，属 **实验性**。

## 相关

- 签名提取：`scripts/docs/extract-api.sh` · [API 页](/api/collections)
