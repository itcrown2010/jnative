# 装箱 / ArrayList / HashMap（2026-10-09）

## 范围（本轮）

| 项 | 状态 |
|---|---|
| `Integer` / `Long` / `Boolean` | 已实现（`BoxingLib.java`） |
| `Float` / `Double` 薄包装 | 已实现（无 valueOf 缓存） |
| 自动装箱 / 拆箱 | 赋值、参数、返回、增强 for、二元运算 |
| `ArrayList<T>` | add/get/set/removeAt/removeValue/size/isEmpty/clear/iterator |
| `HashMap<K,V>` | put/get/containsKey/remove/size/clear/entryIterator |
| 排序 / 并发集合 / Stream | **不在本轮** |
| `ArrayList<int>` 特化 | **仍支持**（非装箱路径） |

泛型实参：引用类型用 `ArrayList<Integer>`；`ArrayList<int>` 为特化扩展，语义不同，勿混为一谈。

## 装箱契约

- `Integer.valueOf` / `Long.valueOf`：缓存 **[-128, 127]**（与 Java 身份规则对齐）。
- `Boolean.valueOf`：`TRUE` / `FALSE` 单例。
- `==`：引用身份；数值比较请拆箱或 `equals`。
- 拆箱 `null` → `NullPointerException`（`nullCheck`）。
- 重载：exact → 原始拓宽 → 装箱（`int` 优先 `f(int)` 而非 `f(Integer)`）。
- 包装对象为普通托管对象，参与 GC。

## 集合契约

**ArrayList**

- 扩容 ×1.5；溢出抛 `IllegalStateException`，不留下半更新列表。
- `removeAt(index)` / `removeValue(value)` 分名，避免 `Integer` 歧义。
- `contains`/`indexOf`：先 `==`，再 `equals`（特化 `int` 时 equals→`==`）。
- `clear`/`removeAt` 置 `null` 释放引用。
- 迭代：`modCount` 快速失败；不宣称并发安全。

**HashMap**

- 开地址 + tombstone；负载 0.75 扩容。
- `containsKey` 用槽位查找，**不**用 `get==null` 推断。
- **不支持 null 键**；允许 null 值。
- 负 `hashCode` / `MIN_VALUE`：`spreadHash` 取安全非负索引。
- `entryIterator()`：无序；结构修改 fail-fast。
- **可变键**：put 后修改影响 hash/equals 的字段 → 查找未定义。

## 迭代

```java
for (Integer v : list) { sum = sum + v; }
```

- 一次 `iterator()`，每轮 `hasNext`/`next`。
- 耗尽 `next` → `NoSuchElementException`。
- 结构修改 → `ConcurrentModificationException`（best-effort fail-fast）。

## API 清单（支持）

`Integer`/`Long`/`Boolean`/`Float`/`Double`: `valueOf`, `*Value`, `equals`, `hashCode`  
`ArrayList`: 上表 + `addAt`/`indexOf`/`contains`  
`HashMap`: 上表 + `keysArray`/`entryIterator`/`HashMapEntry`

## 测试 / 示例

- `BoxingCollectionsEndToEndTest` — vs javac，O0/O1  
- `CollectionsEndToEndTest` — 特化路径回归  
- `examples/third-party-collections-lib/` — `.jnpkg` 源码包引用  

## 平台

| | Native | WASM |
|---|---|---|
| 装箱 + ArrayList/HashMap | 已测 | 未在本轮复验；按未知处理直至专用用例 |
| println(Integer) | 不支持（先 `.intValue()`） | 同左 |

## 剩余限制

- 无完整 `java.util` / Stream / Concurrent\*  
- HashMap 无 null 键、无有序遍历承诺  
- Float/Double `equals`/`hashCode` 非 bit-complete Java 兼容  
- 条件表达式中复杂装箱推导若失败 → 明确诊断（依赖现有 `?:` 规则）
