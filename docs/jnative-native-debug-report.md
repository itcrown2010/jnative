# JNative 原生调试交付报告

日期：2026-10-09（在 2026-10-08 基础报告上续）

## 支持矩阵

| 能力 | 状态 |
|------|------|
| `--debug` 默认 `-O0`（无显式 `-O*`） | **已实现 + 单元测试** |
| 显式 `-O*` + `--debug`（保留优化并警告） | **已实现 + 单元测试** |
| LLVM DI（含 DICompositeType / 多 DIFile / dbg.declare） | **已实现 + dwarfdump 验证** |
| clang `-g`、不剥离符号、`.dSYM` | **已实现** |
| `<exe>.jn-debug.json` 布局目录（含项目 staging 发布） | **已实现** |
| stdlib 前置行回映射 | **已实现**；大项目 DB/Time **中部插入**时，插入点之后的文件行号可能偏移 |
| 多文件拼接 → 原始 `.java`（小项目 + ERP 采购相关文件） | **命令行已验证** |
| LLDB 单文件 / 多文件基础调试 | **通过** |
| 对象 / 继承 / null / 环 + String/数组格式化 | **通过**（`lldb_object_verify.sh`） |
| GC 后引用可读（地址可搬迁） | **通过**（`lldb_gc_verify.sh`） |
| 未处理异常钩子 + throw 处用户栈 | **通过**（`lldb_exception_verify.sh`） |
| ERP 采购链练习库调试 | **通过**（`lldb_purchase_debug_verify.sh`） |
| VS Code CodeLLDB + 格式化器 `initCommands` | **代码已实现** |
| VS Code F5 UI 截图 | **环境阻塞**（无 `code` CLI） |
| Linux/Windows、WASM、移动容器 | **不宣称覆盖** |

## 本轮命令行实测（macOS arm64）

```text
./examples/debug/lldb_verify.sh
→ PASS

./examples/debug/multifile/lldb_verify.sh
→ PASS

./examples/debug/lldb_object_verify.sh
→ PASS（中文 String、截断、int[]、Dog 继承、null、环、Dog[]）

./examples/debug/lldb_gc_verify.sh
→ PASS（GC 后地址变化且字段仍正确）

./examples/debug/lldb_exception_verify.sh
→ PASS（uncaught 钩子 + throw 用户栈 + RuntimeException 中文消息）

./examples/erp/scripts/lldb_purchase_debug_verify.sh
→ PASS（ErpPurchasing/ErpPosting 原始文件断点，poId=1，PASS，purchase_orders≥1）
```

## 代码改动摘要（本轮）

- `LlvmDiBuilder`：用户类 / String / 数组 `DICompositeType`，局部量附着 Java 类型名
- `DebugCatalogEmitter` + `CompilerSession.publishSiblingDebugCatalog`
- 继承字段偏移与 LLVM GEP 对齐（`ObjectLayoutBuilder`）
- 标签禁止附着 `!dbg`（修复 `--debug` 下 `divtrap` IR 校验失败）
- `DiagnosticJson.userLineOffset`：容忍 stdlib 中部插入
- `tools/lldb/jnative_formatters.py`（内存只读、有界）
- `jnative_debugger_on_uncaught` 空钩子
- VS Code `launchConfig` 自动 `command script import` 格式化器
- 示例：`ObjectDebugDemo` / `GcDebugDemo` / `ExceptionDebugDemo` + ERP launch

## 限制（明确未宣称）

- CodeLLDB 无 Java 风格「异常断点」面板；用符号断点
- GC 执行中途暂停的堆一致性
- 变量展开不调用目标方法；部分对象在序言处参数尚未写入 alloca
- ERP 全量 `build --debug` 时 browser 目标可能失败（与本轮无关的既有问题）；server 产物足够
- 正式数据库、HTTP 长暂停重试：勿用于本教程路径

详述：`docs/jnative-native-debug-objects.md`  
F5 基础：`docs/jnative-native-debug-tutorial.md`
