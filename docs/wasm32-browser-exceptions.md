# wasm32-browser 异常、finally 与资源释放

作者：张晓达

与 native LLVM 共用同一套语言语义：显式 pending 异常 + CFG（`jnative_exception_*` / `jnative_completion_*`）。不使用 WASM exception-handling 提案指令，避免双路径。

## 后端方案

| 选项 | 结论 |
|------|------|
| WASM 原生 EH（`try`/`catch`/`throw` opcode） | **不采用**。浏览器已广泛支持（见下），但 IR/native 已是 pending+CFG；双实现未经验证。 |
| 显式 pending + 控制流 | **采用**。Throw 写 `$jn_pending_exc`；`maybeCheckPending` 分支到 catch/finally 或 unwind `return`。 |

### 浏览器支持矩阵（WASM EH — 本后端未依赖）

| 引擎 | 版本（EH 可用） | 本后端 |
|------|-----------------|--------|
| Chrome / Edge | ≥ 95 | 未使用 EH；用 pending |
| Firefox | ≥ 100 | 同上 |
| Safari | ≥ 15.2 | 同上 |
| Node.js | ≥ 17（V8） | 验收用 Node 24.1.0 执行真实 WASM |

若未来切换到 EH，需显式门控特性探测；当前构建不会因缺少 EH 而静默改语义。

## 语言语义（与 native 一致）

- `throw` / `throw null` → NPE（`jnative_exception_throw`）。
- `catch`：声明序 + `instanceof` 子类型匹配；未匹配继续 pending 传播。
- 跨普通调用、虚调用、接口调用、闭包（`InvokeCallable` / `forEach` lambda）传播。
- `catch` 内 `throw e` 重新抛出。
- finally：normal / return / break / continue / 异常出口均执行；return 先求值；finally 正常完成保留原退出原因；finally 自身突然完成替换退出原因；嵌套内→外。
- try-with-resources：顺序初始化、逆序关闭；init 失败关闭已成功资源；close 失败 `addSuppressed`。

## GC 与根栈

- 每个函数在 `$exit` 前 `jn_gc_frame_pop`（含 unwind `br $exit`）。
- `$jn_pending_exc` 为 GC 根；待传播异常在 alloc/GC 后存活（`cleanupGc` 验收）。
- completion 返回指针单独保存在 `$jn_comp_ret_ptr`（finally 内分配不破坏原返回引用）。
- JS 回调重入：每次 `jnative_callback` 独立帧；返回后 pop。

## JS 边界契约

| 方向 | 行为 |
|------|------|
| JS 宿主失败 | 胶水 `raiseHostError` → `jn_raise_host_error(msg)` 设 pending（消息含操作种类，不含完整敏感参数）。随后 IR `maybeCheckPending` 可捕获。 |
| 未处理 JNative 异常 | `jnative_start` / `jnative_callback` 结束时 `jnative_exception_uncaught` → import `report_uncaught` → JS `Error`（`name = JNativeException`）。先清 pending 再抛，避免残留。 |
| 直接调用导出方法 | 返回后若 pending，宿主应 `jn_exception_uncaught()` 或胶水 `callChecked`。不静默吞掉。 |
| WASM trap | `WebAssembly.RuntimeError` 等；**不可**当作可恢复业务异常。trap 后不得假设运行时仍安全。 |
| 恢复范围 | 仅语言 pending 异常可 catch；trap / 宿主硬失败后应停止或重建实例。 |

## 源码定位

- IR 保留 `SourceLocation`；native 有 DI。
- wasm32-browser：**完整 DWARF/source map 尚未接到浏览器调试器**。开发诊断：`--keep-temps` 看 `app.wat` 函数名（`_JN_Class_method`）+ 异常 message；勿编造源码栈帧。

## 运行示例

```bash
mvn -DskipTests package
examples/browser/exc-stress/scripts/verify.sh   # default + -O0，真实 instantiate
# 浏览器：将 dist/ 用任意静态服务器打开 index.html
```

## 实测结果（本轮）

```text
PASS exc-stress [default] { twrSuppress: 71, uncaughtMsgs: [ 'uncaught-probe' ] }
PASS exc-stress [O0]      { twrSuppress: 71, uncaughtMsgs: [ 'uncaught-probe' ] }
PASS gc-stress / float-calc / order-list（回归，含新 import report_uncaught）
Node v24.1.0（V8）
Chromium（cursor-ide-browser）打开 dist/index.html：
  catchExact=1 catchSuper=2 propagate=3 finallyPaths0=10 nestedFinally=123
  twrOrder=21 cleanupGc=8 ifaceThrow=9 closureThrow=10 OK
```

覆盖：跨函数、父类 catch、接口、闭包、嵌套 finally、TWR 序/init 失败/抑制、finally 中 GC、未处理异常 JS 边界、-O0 与默认一致、真实浏览器执行。

## 剩余限制

- 未使用 WASM EH；不提供双后端。
- 完整浏览器源码映射未实现。
- 自定义 SAM（非标准库函数式接口）的 `() -> int` lambda 可能被语义分析拒绝；接口实现类与 `forEach` 等标准 SAM 可用。
- checked `throws` / multi-catch / precise rethrow / TWR 已有变量形式：与语言层相同，未支持。
- 数组越界等部分路径仍可能 `unreachable`（WASM trap），尚未全部改为语言 AIOOBE。
