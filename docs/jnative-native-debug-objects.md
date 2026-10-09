# JNative 原生调试：对象 / 字符串 / 数组 / 异常 / ERP

作者：张晓达

日期：2026-10-09（macOS arm64 实测）

## 快速开始

```bash
# 1) 对象 / 字符串 / 数组
./examples/debug/lldb_object_verify.sh

# 2) GC 前后引用
./examples/debug/lldb_gc_verify.sh

# 3) 未处理异常停靠
./examples/debug/lldb_exception_verify.sh

# 4) ERP 采购链（独立 SQLite 练习库）
./examples/erp/scripts/lldb_purchase_debug_verify.sh
```

VS Code / Cursor：打开仓库根目录，安装 CodeLLDB（`vadimcn.vscode-lldb`）与 JNative 扩展，使用：

- `examples/debug/.vscode/launch.json` → ObjectDebugDemo / DebugDemo
- `examples/erp/.vscode/launch.json` → ERP logic-test（练习库）

扩展会在 `initCommands` 中加载 `tools/lldb/jnative_formatters.py`。

## 对象与继承

- 编译器在 `--debug` 下为用户类发出 `DICompositeType`（含 `__meta` 与扁平化字段，含继承字段）。
- 旁路目录：`<exe>.jn-debug.json`（字段偏移 / 数组元数据）。
- LLDB 格式化器按 **ClassMeta.name** 识别实际类型；`Animal` 引用指向 `Dog` 时显示 `Dog{...}`。
- 空引用显示 `null` / `nullptr`；循环引用以地址或 `<cycle>` 截断，不无限展开。
- 读失败显示 `<unreadable>`，不伪造默认值。

## 字符串与数组

运行时布局（内存只读，不调用目标方法）：

| 类型 | 布局 |
|------|------|
| String | `{ meta@0, i32 len@8, UTF-8 bytes@16 }` |
| 数组 | `{ meta@0, i32 len@8, elements@16 }` |

- UTF-8 含中文；空串 `len=0`；空引用 `null`。
- 超长字符串默认截断至 256 字节并标注原始 `len` 与 `<truncated>`。
- 数组展示类型名（如 `array.i32`）、长度与最多 16 个元素；支持对象数组与嵌套引用。
- 损坏的负长度 / 超大读取受 `MAX_READ` 限制。

## GC 与变量生命周期

- 默认 GC2：young 区可移动。断点停在 `System.gc()` 前后时，格式化器通过**当前根槽**读取，地址可变、字段值应保持。
- `-O0` 下未赋值局部量可能显示垃圾；已离开作用域的量不保证可恢复——以 LLDB 是否给出位置为准，不伪造。
- **不宣称** GC 执行中途任意暂停时的完整堆一致性。

## 异常

| 场景 | 做法 |
|------|------|
| 未处理异常停止 | `breakpoint set --name jnative_debugger_on_uncaught`（空钩子，关闭调试时语义不变） |
| 查看用户调用栈 | 在 `jnative_exception_throw` 下断（TLS 传播后 uncaught 处原生栈已收拢） |
| 所有抛出 | 同上 throw 符号（CodeLLDB 无 Java 风格异常断点 API） |

区分：

1. **JNative 异常** — 打印类型 / 消息 / `jn_ex_print_stack`，进程 `exit(1)`
2. **运行时主动终止** — `jnative_throw_*` / abort 文案
3. **原生崩溃** — SIGSEGV 等，不包装成 JNative 异常

## ERP 采购链调试

练习库（**禁止**正式库）：

```bash
export DATABASE_URL='sqlite:./build/erp/debug-practice.db'
java -jar target/jnative.jar build --project examples/erp --debug
# 浏览器目标可能失败，只要 erp-server + .dSYM 即可
```

推荐断点：

- `ErpPurchasing.java` → `createDraft` / `approve`
- `ErpPosting.java` → `postGoodsReceipt`

验证脚本使用 `--logic-test`（进程内，无 HTTP）。若调试 HTTP 服务：暂停可能导致客户端超时；`ErpLogicTest` / 过账接口的幂等键可避免重复过账。变量查看**不会**执行数据库写入。

说明：含 Database/Time stdlib **插入**时，位于插入点之后的源文件（如部分 `ErpServer` 方法）行映射可能偏移；采购链相关文件在插入点之前，已实测命中原始 `.java`。

## 支持矩阵（本轮实测）

| 能力 | macOS arm64 | 其他 |
|------|-------------|------|
| 基础断点 / 单步 / 栈 / int | **通过** | 未测 |
| 多文件行号（小项目） | **通过** | 未测 |
| DI 复合类型 + 格式化器 | **通过** | 未测 |
| 中文 / 超长字符串 / 数组 | **通过** | 未测 |
| 继承 / null / 环 | **通过** | 未测 |
| GC 后引用仍可读（可搬迁） | **通过** | 未测 |
| 未处理异常钩子 + throw 栈 | **通过** | 未测 |
| ERP 采购链练习库 | **通过** | 未测 |
| VS Code F5 UI 截图 | 环境无 `code` CLI，需本机补截图 | — |
| Linux / Windows / WASM | **不宣称** | — |

## 相关文件

- `src/main/java/io/jnative/backend/llvm/LlvmDiBuilder.java`
- `src/main/java/io/jnative/backend/llvm/DebugCatalogEmitter.java`
- `tools/lldb/jnative_formatters.py`
- `examples/debug/ObjectDebugDemo.java` / `GcDebugDemo.java` / `ExceptionDebugDemo.java`
- `docs/jnative-native-debug-tutorial.md`（F5 基础）
- `docs/jnative-native-debug-report.md`（总交付报告）
