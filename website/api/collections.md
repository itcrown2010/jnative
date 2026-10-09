# API：数值与集合

**源码**：`src/main/resources/stdlib/collections/CollectionsLib.java`、`stdlib/math/`

## 已提取的公开类型（节选）

运行提取脚本后查看完整列表。源码中可见：

- 接口：`Iterable`、`Iterator`、`Collection`、`List`、`Set`、`Map`、`Queue`、`Deque`、`Comparator`、`Consumer`、`Function`、`Predicate`、`Supplier` 等
- 类：`ArrayList`、`HashMap`、`HashSet`、`ArrayDeque`、`StringBuilder`、`Arrays`、`Collections`、`ThreadPoolExecutor` 等

## ArrayList\<T\>（摘要）

| 项 | 说明 |
|----|------|
| 典型方法 | `add`、`get`、`size` 等（以源码为准） |
| 异常 / 边界 | 越界行为 **待完善**（请写最小测试） |
| 线程 | **非**默认线程安全 |
| 目标 | 原生实验性；WASM 实验性 |
| 版本 | 0.32 |

## 数值 math 模块

见 `_generated/math.txt`（若模块有可解析声明）。无生成内容时标 **待完善**。
