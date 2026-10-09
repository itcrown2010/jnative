# JNative 语言核心：多文件名称解析与访问控制（2026-10-09）

作者：张晓达

## 本轮目标与验收

里程碑：**多文件名称解析、访问控制、真实源码诊断**；第三方源码库多包项目可编译运行；非法跨包访问与歧义有准确诊断。

## 修改文件及目的

| 区域 | 目的 |
|---|---|
| `semantic/SemanticAnalyzer.java` | FQN 索引、import/同包解析、成员/类型可访问性、覆盖可见性、歧义调用诊断、错误参数个数回传 |
| `semantic/Visibility.java` | public/protected/package/private 规则 |
| `ast/*` + `parser/Parser.java` | package/import AST；拒绝 static/wildcard import；顶层类型禁止 private/protected |
| `source/SourceFiles.java` | `.jn` / `.java` 统一发现 |
| `incremental/CompilerSession.java` 等 | 源码发现、入口、包依赖根目录支持两种后缀 |
| `cli/ProjectCli.java` | `jnative new` 默认 `Main.jn` |
| `tools/vscode-jnative` | 语言扩展关联 `.jn`/`.java` |
| `examples/multipackage-resolve/` | 多包 + 第三方库正例与负例 |
| `PackageAndAccessTest` / `MultipackageResolveIntegrationTest` | 前端与集成验证 |

## 语言支持清单（增量）

详见更新后的 [`docs/language-support.md`](language-support.md)。

要点：

- **SUPPORTED：** `package`、显式类型 `import`、同包引用、类型位置 FQN、多文件前向引用（先声明后分析）、第三方源码包类型、重复类型诊断、访问控制检查、`.jn`/`.java`。
- **明确拒绝：** `import static`、`import pkg.*`、顶层 `private`/`protected` 类型。
- **未做（另列）：** 表达式位置的点分 FQN 接收者（`com.acme.Foo.bar()`）、嵌套类型、完整 Java 泛型兼容。

## Java 兼容范围与已知差异

| 规则 | JNative |
|---|---|
| 语言 package vs 包管理坐标 | **严格分离**（`com.demo.greet` ≠ `demo/greetkit`） |
| 顶层类型可见性 | 仅 public / package-private（与 Java 一致） |
| 成员 public/protected/package/private | 已检查；protected = 同包或子类 |
| 覆盖不得削弱可见性 | 已检查 |
| 多文件 | 仍经拼接编译单元 + **按类型保留** package/import；诊断经 line map 回映 |
| 后缀 | `.jn` 与 `.java` 同语义；不表示完整 Java SE |

## 示例

`examples/multipackage-resolve/` — 见该目录 README。

## 实测结果（2026-10-09）

```text
mvn -q -Dtest=PackageAndAccessTest,MultipackageResolveIntegrationTest,SemanticAnalyzerTest,SourceLibPackageIntegrationTest test
# BUILD SUCCESS

原生输出（multipackage 正例）:
400
3
8
```

负例：`BadAccess` → package/not accessible（含行列）；`AmbiguousCall` → ambiguous method call。

## 剩余问题（优先级）

1. **P1** 表达式位置全限定接收者（`pkg.Type.staticMethod`）解析。
2. **P1** 拼接单元逐步改为真正多 `CompilationUnit` 联合分析（减少边界特例）。
3. **P2** 运算语义 / 重载精细化差异测试（下一里程碑）。
4. **P2** 浮点完整语义与 opt 一致性专项。
5. **P3** 嵌套类型、通配符/静态导入（若需要再开 RFC）。
