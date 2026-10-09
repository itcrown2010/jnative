# float / double 数值语义（2026-10-09）

作者：张晓达

## 验收结论

类型检查、常量折叠、LLVM 原生与优化级别（O0 / 默认）对同一套用例与 **javac/java** 对齐；WASM 单独经 `WasmBrowserFloatTest` / `float-calc` 验证。浮点→整数转换在两端均避免 trap/poison（原生：`jnative_f*_to_i*`；WASM：`trunc_sat`）。

## 一、能力基线

| 能力 | float (binary32) | double (binary64) |
|---|---|---|
| 字面量小数 / 指数 | 已实现 + 测试（`1.5f` / `1e-3`） | 已实现 + 测试（`1.5` / `1.5d`） |
| 后缀 `f`/`F`、`d`/`D` | 已实现 | 已实现 |
| 十六进制浮点 / `_` 分隔 | **硬错误** | **硬错误** |
| 二元提升（与 int/long/float/double） | 已实现（Java 序：double>float>long>int） | 同左 |
| 一元 `+`/`-`（含 `-0`） | 已实现（`-x` → `(-0)-x`） | 同左 |
| 算术 `+ - * / %` | 已实现；`%` = Java remainder（LLVM `frem` / WASM `f*.rem`） | 同左 |
| 比较（NaN：`==` 有序，`!=` 无序） | LLVM `oeq`/`une`/`olt`…；WASM `eq`/`ne`/`lt`… | 同左 |
| 浮点除零 → Inf/NaN | **不**抛 ArithmeticException | 同左 |
| 显式 cast 互转 / → 整数 | 朝零；NaN→0；饱和 | 同左 |
| 常量折叠 | `IrUtil.foldBinaryFloat` / `foldCompareFloat`（Java `float`/`double` 运算） | 同左 |
| 调试 DI | `DW_ATE_float` 32/64 | 同左 |
| println / String 拼接 | `snprintf %g`（**非**逐字符 `Double.toString`） | 同左 |
| 解析 | `strtod` 整串消费，否则 IllegalArgument | 同左 |
| Math abs/min/max | float + double 重载 | double + libm |

## 二、明确契约

- **格式**：IEEE 754 binary32 / binary64；默认舍入（round-to-nearest-even）。非默认舍入模式、flush-to-zero 等 **不在支持范围**。
- **禁止**：fast-math、随意重结合、擅自 FMA 融合（链接仅 `-O*`，无 `-ffast-math`）。
- **余数**：Java `%`（截断商），**不是** IEEE 754 remainder（`Math.IEEEremainder` 未提供）。
- **财务**：金额用整数最小货币单位或 `Decimal`；本能力面向科学计算与近似统计。见 `examples/NumericStats.java`。

## 三、转换

| 方向 | 行为 |
|---|---|
| 整数 → float/double | `sitofp` / WASM `convert_*_s` |
| float ↔ double | `fpext` / `fptrunc`；`promote` / `demote` |
| float/double → int/long | NaN→0；朝零；超出范围饱和到类型 min/max |
| 再窄化到 byte/short/char | 先到 int，再按整数窄化（char 零扩展掩码） |

原生不用裸 `fptosi` 处理可能越界/NaN 的输入。

## 四、平台差异（可见）

| 项 | Native (LLVM) | wasm32-browser |
|---|---|---|
| fp→int | `jnative_f64_to_i32` 等 | `i*.trunc_sat_f*_s`（语义同：NaN→0/饱和） |
| 格式化 | `%g` via snprintf | 主机 JS 显示（示例 UI）；算术在 WASM 内 |
| Math / parse | libm + strtod | 浏览器路径无完整 Math/parse 承诺 |
| 验证入口 | `FloatEndToEndTest` | `WasmBrowserFloatTest` + `examples/browser/float-calc` |

## 五、格式化差异（勿标完全兼容）

`String.valueOf` / 拼接浮点使用 C `%g`，**不保证**与 `Double.toString` / `Float.toString` 逐字符一致（位数、科学计数法切换、`-0` 文本等）。NaN/Inf 文本依赖 libc。需要稳定文本协议时自行约定格式。

## 六、测试

```bash
mvn -Dtest=FloatEndToEndTest,PrimitiveOpsEndToEndTest,MathEndToEndTest,WasmBrowserFloatTest test
./examples/browser/float-calc/scripts/verify.sh
```

覆盖：正常数、次正规、±0、NaN、Inf、浮点除零、`%`、比较、提升/重载/三元、转整数边界、常量折叠 vs 运行时、O0 vs O1、与 Java 对照。NaN **不**要求载荷位模式一致。

## 七、剩余限制

- 十六进制浮点字面量、数字分隔符：硬错误
- 完整 `Float`/`Double` 装箱与全部 `Math` float 重载：未承诺
- 格式化非 Java 逐字符兼容
- WASM：无完整 println 浮点宿主；以 float-calc 导出函数验证
- 非默认舍入 / FTZ / DAZ：不支持
