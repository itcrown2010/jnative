# Switch 与基础 Enum（2026-10-09）

作者：张晓达

## 表示方案（enum）

| 项 | 选择 |
|---|---|
| 对象模型 | **托管对象**（非整数伪装指针） |
| 实例字段 | `$name: String`, `$ordinal: int`（私有） |
| 常量 | `public static final` 字段，在 `jnative_clinit` 中 `new` + 构造 |
| GC | 静态引用字段登记为 static root |
| 与 Java | **不是** `java.lang.Enum` 子类；无用户构造器/成员/常量类体 |

## 支持边界

**支持：** 整数 `switch`（byte/short/char/int）；基础 `enum`；enum `switch`；`name`/`ordinal`/`values`/`valueOf`；穿透、`break`、嵌套循环 `continue`。

**明确拒绝：** switch 表达式 / `->`；字符串 switch；模式匹配；enum 参数与成员；`new EnumType()`；long/boolean/浮点 switch。

## 测试

- `SwitchEnumEndToEndTest`（语义负例 + 原生 e2e，`-O0`/默认一致）
- 示例 `examples/OrderStatusDemo.java`

## WASM

含 `println` 的程序在 `wasm32-browser` 上仍编译期拒绝。switch/enum 的 IR 控制流与静态初始化走同一原生路径；浏览器端完整运行需后续无 println 示例。
