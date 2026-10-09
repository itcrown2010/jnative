# 多编译单元架构变更（2026-10-09）

作者：张晓达

## 里程碑

**独立解析 → 项目级符号登记 → 多文件运行**（生成代码轮次/增量精化后续）。

## 能力基线（核实）

| 能力 | 原状 | 现况 | 位置 |
|---|---|---|---|
| 用户多文件 | `CompilerSession` 拼接 `*-unit.java` | 每文件 `SourceUnit` 独立 parse | `collectModuleProgramSources` |
| 标准库 | `prependStdlibs` 文本前置 | `collectStdlibSourceUnits` 独立 CU | `StdlibIncluder` |
| package/import | 拼接单元内切换 package | 每类型保留 package/import；跨 CU 登记 | `SemanticAnalyzer.analyze(List)` |
| 类型身份 | FQN `ClassSymbol.name()` | 不变；IR 用扁平 class 列表 | `TypedCompilationUnit.units` |
| 诊断位置 | line map / 用户行偏移 | 解析时写入真实 path；stdlib=`<stdlib>/…` | `MultiUnitFrontend.parseUnit` |
| 增量缓存 | 按文件指纹 | 仍按文件；编译不再吃拼接缓冲 | `CompilerSession` |

## 管线

1. 收集用户 / 依赖 / 按需标准库 /（可选）生成源 → `ProgramSources`
2. **类型名发现**（轻量词法扫描 `class`/`interface` 简单名）→ 供各 CU 独立 parse 识别跨文件类型（含 stdlib），**不拼接源码**
3. 每单元独立 Lexer+Parser → `List<CompilationUnit>`（保留 path / SourceLocation）
4. `SemanticAnalyzer.analyze(units)`：declare → link → members → bodies
5. `TypedCompilationUnit.unit()` = 扁平 AST（供 IR）；`units()` = 原始文件 AST
6. 优化器与后端消费项目级 IR（不变）

## 修改文件（核心）

- `compiler/MultiUnitFrontend.java`, `ProgramSources.java`
- `compiler/JNativeCompiler.java`, `CompilerOptions.java`
- `semantic/SemanticAnalyzer.java`, `TypedCompilationUnit.java`
- `stdlib/StdlibIncluder.java`
- `incremental/CompilerSession.java`
- `ast/ProgramAst.java`
- 示例 `examples/multi-cu-resolve/`
- 测试 `MultiUnitCompileTest`
- 契约 `docs/jnative-language-semantics.md`

## 验收对照

| 标准 | 结果 |
|---|---|
| 用户/标准库/第三方独立 CU | 是（`<stdlib>/…` + pkg-cache 提取源；无 `*-unit.java` 拼接语义输入） |
| 文件顺序不影响正确性 | `fileOrderDoesNotChangeSemanticDiagnostics` / `nativeMultiCuEndToEnd` |
| 诊断指向原始文件 | `errorLocationIsUserFileNotStdlib`（含 stdlib CU 加载） |
| 原生输出 | `examples/multi-cu-resolve` → `42 / 7 / 42`；`nativeMultiCuEndToEnd` |
| `-O0` 与默认 opt | `o0AndDefaultOptAgree` |
| 语义契约可验证 | `docs/jnative-language-semantics.md` |

## 实测（本轮）

```text
mvn -q -Dtest=MultiUnitCompileTest,PackageAndAccessTest,MultipackageResolveIntegrationTest,SemanticAnalyzerTest test
→ OK

publish demo/kit + build multi-cu-app → OK
./examples/multi-cu-resolve/app/build/multi-cu-app → 42\n7\n42
```

## 剩余

1. IDE `analyze` 多缓冲文档版本丢弃策略精化  
2. 生成代码多轮收敛的专项回归  
3. 增量「正确失效」矩阵；不足时已可完整重建  
4. 调试断点跨文件样例自动化（LLDB）  
5. 浮点 / 除零 / 溢出专项 Java 对照  
6. WASM 多 CU 未随本轮原生成功推断；需单独矩阵  
