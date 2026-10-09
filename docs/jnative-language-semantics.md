# JNative 语言语义契约（多编译单元里程碑）

**日期：** 2026-10-09  
**规则：** 接受 `.java` / `.jn` **不等于** Java SE 兼容。下列状态以源码与测试为准。

## 状态图例

| 标记 | 含义 |
|---|---|
| **Java-一致** | 已实现且与 Java 同输入同结果（有测试） |
| **明确不同** | 已实现但故意/已知与 Java 不同（有说明+测试或样例） |
| **暂不支持** | 应报错或未实现；不得静默错误 |
| **未验证** | 代码路径存在，缺少对照测试 |

---

## 1. 编译模型

| 契约 | 状态 | 证据 |
|---|---|---|
| 每源文件独立 lex/parse | Java-一致（架构） | `MultiUnitFrontend.parseUnit` |
| 项目级 FQN 符号登记 | Java-一致（子集） | `SemanticAnalyzer.analyze(List)` |
| 文件顺序不影响解析 | Java-一致（子集） | `MultiUnitCompileTest.fileOrderDoesNotChangeSemanticDiagnostics` |
| 标准库为独立 CU，不前置拼进用户文件 | 明确不同（实现） | `StdlibIncluder.collectStdlibSourceUnits` |
| 第三方源码经包管理加入 CU | 支持 | `CompilerSession.collectModuleProgramSources` |
| 拼接用户源码实现多文件 | **已移除**（会话路径） | 不再调用 `materializeModuleCompileUnit` 拼接 |

## 2. 名称与访问

| 契约 | 状态 | 证据 |
|---|---|---|
| package / 显式 import / 同包 | 支持 | `PackageAndAccessTest` |
| 类型位置 FQN | 支持 | 同左 |
| 表达式 FQN 静态调用 `pkg.Type.m()` | 支持 | `MultiUnitCompileTest.fqnStaticCallWorks` |
| 同简单名不同包 | 支持 | `sameSimpleNameDifferentPackagesOk` |
| 重复 FQN | 报错 | `duplicateFqnRejected` |
| 继承环 | 报错 | `inheritanceCycleDiagnosedAcrossFiles` |
| public/private/protected/package | 支持（L2） | `PackageAndAccessTest` |
| stdlib 省略 `public` 的类型/成员对命名包可见 | **明确不同**（导出为 PUBLIC） | `SemanticAnalyzer.exportStdlibMember`；订单多包示例 |
| wildcard / static import | 暂不支持（硬错误） | Parser |

## 3. 数值与运算

| 契约 | 状态 | 证据 |
|---|---|---|
| `int` 除法向零截断 | Java-一致 | `o0AndDefaultOptAgree`（7/3→2） |
| `int` 余数符号随被除数 | Java-一致 | 同左（7%3→1） |
| 乘加优先级 | Java-一致 | 同左（7+3*2→13） |
| `-O0` 与默认 opt 同结果 | 支持 | `PrimitiveOpsEndToEndTest` |
| 整数显式 cast（byte/short/char/int/long） | Java-一致 | 同左 vs javac |
| `char` 16 位；拓宽零扩展 | Java-一致 | `(int)(char)65535 → 65535` |
| boolean cast | 硬错误 | `booleanCastRejected` |
| 浮点 cast / 提升 | 支持（Java 兼容边界） | `FloatEndToEndTest` / `docs/jnative-float-double.md` |
| 三元 `?:` 右结合；仅求值所选分支 | Java-一致 | 同左 |
| `& \| ^ ~` 整数；boolean 双方求值 | Java-一致 | 同左 |
| `<< >> >>>` 距离掩码 5/6 位 | Java-一致 | 同左 |
| 有符号溢出环绕 | 未验证专项 | 依赖 LLVM |
| 除零 | 未验证专项 | 运行时陷阱路径存在 |
| 十六进制字面量 `0x…` | 暂不支持 | Lexer |

## 4. 字符串

| 契约 | 状态 |
|---|---|
| `length()` / 索引为 UTF-16 **码元** | **Java-一致**（`docs/jnative-string-utf16.md`；`StringEndToEndTest`） |
| `utf8Length()` / `getBytes()` 为 UTF-8 字节 | 支持 |
| `split` 字面分隔符非正则 | **明确不同** |
| `FileInputStream`→`byte[]` 高位字节完整 | Java-一致（`StreamNative.getU8` 逻辑移位；`OrderProcessingEndToEndTest.fileInputStreamUtf8RoundTrip`） |

## 5. 其它边界

| 契约 | 状态 |
|---|---|
| 引用 `==` 为对象身份 | 支持（既有） |
| 布尔短路 | 支持（既有；缺专项差异套件） |
| 异常 / try-with-resources | 支持 L2–L3 |
| 泛型 | 基础特化支持；通配符 EXPERIMENTAL |
| async/await | L2 薄支持 |

---

## 迁移说明

- 项目构建不再生成拼接的 `*-unit.java` 作为语义输入；诊断文件名应为真实路径或 `<stdlib>/…`。
- 仍可用单文件 CLI；内部走同一 `MultiUnitFrontend`。
- 旧依赖「拼接后默认包唯一类名」的库应改为语言 `package` + import（见 `examples/multi-cu-resolve`）。
