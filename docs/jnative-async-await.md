# async/await 状态机（可靠挂起/恢复）

**日期：** 2026-10-09  
**前置：** 闭包里程碑、`JnFuture`/EventLoop 回调契约（`docs/jnative-functional-callbacks.md`）

## 审计结论（实现前）

此前链路仅为「存 state、慢路径返回**被等待** Future」：

- `jnative_async_awaitCooperative` 不注册 continuation。
- 无恢复入口 / 无结果 Future 闭环。
- `AsyncLoweringPass` 慢路径 `return a.future()`，会把 10 误当成方法结果。

现已形成闭环：挂起 → 注册 continuation → 调度恢复 → 完成**方法自身**的 Future。

## 语法与语义

沿用既有语法：`async T m(...)` / `await expr`。

| 规则 | 行为 |
|---|---|
| 返回类型 | `async T` 对外为 `Future<T>`（禁止再写 `async Future<T>`） |
| 已完成 | 快路径取结果，继续执行（不重新跑方法前半段） |
| 未完成 | 挂起；**不**阻塞事件循环线程 |
| 最终结果 | 完成方法自己的 result Future；**不是** awaited Future |
| 恢复线程 | 完成方经 drain 队列调用 `name__async_resume`；再入方法本体 |
| 多次 await / 分支 | 每站点独立 `resumeState`；dispatch 按 state 跳转 |
| 取消 | `Future.tryCancel` → state=CANCELLED；await 侧按失败完成方法 Future |

## Future 契约（编译器 builtin）

状态：`0=PENDING 1=SUCCESS 2=FAILED 3=CANCELLED`。

API（intrinsic）：`pending` / `completed` / `failed` / `isComplete` / `getState` / `getCompleted` / `getError` / `tryComplete` / `tryFail` / `tryCancel`。

布局扩展：`result`、`error`、continuation 链表。与 stdlib `JnFuture` 并存：`JnFuture` 仍服务 EventLoop/池；async/await 使用 builtin `Future`。桥接勿 silently 混用状态机。

## 状态机（AsyncFrame）

每调用独立帧：

- `state` / flags（continuation 至多恢复一次）
- `resultFuture`（方法结果）
- `awaitResult` / 挂起中的 awaited Future
- `resumeFn` → `mangled__async_resume`
- ref/scalar spill slots（跨 await 活跃变量）

慢路径返回 `resultFuture`。完成与注册竞争：已完成则立即 schedule；`flags` 防双恢复。立即完成链经 **drain 队列**，避免递归爆栈。

## 异常与资源

- await 失败：方法 Future → FAILED；恢复路径 `rethrow`（当前：完成方法 Future 后 throw，避免落入续体）。
- finally：挂起本身不跑 finally；方法真正退出（complete）时才走既有 return/finally 降低路径。
- try-with-resources：资源引用经 spill 保活；**持锁跨 await、线程绑定资源**未承诺——EffectAnalyzer 已对 SUSPENDS+BLOCKS 告警。

## GC 与调度

- 活跃帧挂在 `jn.async.live_frames`；`jnative_async_visit_roots` 挂入 static root visitor。
- 完成/unlink 后从 live 列表移除。
- 回调不在 Future 内部锁中执行（当前实现无用户锁；completion 后 schedule）。

## 示例与测试

| 项 | 说明 |
|---|---|
| `examples/async/AwaitAddFive.java` | await 10 → +5 ⇒ **15**；副作用 2 |
| `examples/async/AsyncSuspendDemo.java` | 挂起期间 EventLoop 定时器仍跑 |
| `AsyncAwaitEndToEndTest` | O0/opt；延迟恢复；双 await；失败；WASM 拒绝 |

```bash
mvn -q -Dtest=AsyncAwaitEndToEndTest,AsyncSemanticTest,AsyncFutureSemanticTest test
```

## 目标边界

| 后端 | 状态 |
|---|---|
| Native | 验收 |
| wasm32-browser | **不支持**（编译失败/诊断）；状态机与调度未接通 |

**async/await ≠ 非阻塞网络。** `Async.submit` 仍是阻塞线程池适配器。

## 剩余限制

- catch 内对 await 失败的精细恢复仍粗（方法 Future 已 FAILED）。
- 跨 await 持锁 / 线程亲和资源：明确不支持。
- `Future` 与 `JnFuture` 尚未单一运行时对象。
- WASM 无 async 状态机。
