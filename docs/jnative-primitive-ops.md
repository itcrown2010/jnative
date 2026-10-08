# 基础类型 cast / 三元 / 位运算 / 移位（2026-10-09）

## 前置

多编译单元回归：`MultiUnitCompileTest` + `PackageAndAccessTest` 通过。

## 缺陷与范围（实现前盘点）

| 项 | 原状 | 目标后端 |
|---|---|---|
| 原始 cast | 解析有 `CastExpression`；语义仅引用类型 | LLVM sext/trunc/zext；WASM 掩码 |
| 三元 | `?` 仅通配符 | CFG + alloca（无 select） |
| `& \| ^ ~` | Lexer 拒绝单 `&`/`\|` | IR `AND/OR/XOR` |
| `<< >> >>>` | 无 | IR `SHL/ASHR/LSHR` + 显式掩码 |

## 类型规则摘要

**Cast（整数）：** byte/short/char/int/long 互转；窄化截断；byte/short 拓宽符号扩展；char 拓宽零扩展。boolean / float/double → 硬错误。

**三元：** 条件 boolean；数值分支用二元算术提升；引用/null 取兼容类型；不支持浮点分支。

**位运算：** 整数提升后运算；boolean `&\|^` 双方求值（非短路）。

**移位：** 左操作数一元提升；距离 `& 31`（int）或 `& 63`（long）；`>>` 算术、`>>>` 逻辑。

## 修改文件

- Lexer/TokenType：`BIT_*`、`COLON`
- Parser：三元 / 位运算 / 移位优先级；`~`
- AST：`ConditionalExpression`；`BinaryOp`/`UnaryOp` 扩展
- SemanticAnalyzer：cast / `?:` / 位运算 / 移位
- IR：`BinOp.XOR/SHL/ASHR/LSHR`；`CastMode.ZERO`
- LLVM / WASM / ConstantPropagation
- 测试：`PrimitiveOpsEndToEndTest`、`WasmPrimitiveOpsLoweringTest`
- 示例：`examples/BitFieldCodec.java`

## 实测

```text
PrimitiveOpsEndToEndTest + WasmPrimitiveOpsLoweringTest → OK
examples/BitFieldCodec 原生输出与 javac/java 一致
wasm32-browser + println → 编译期明确失败（不静默）
WASM 指令 lowering：i32.and/xor/shl/shr_s/shr_u 已覆盖
```

## 剩余

- 浮点 cast / 浮点三元分支
- 十六进制字面量
- 位运算复合赋值
- 浏览器端可运行（非 println）的完整 e2e
