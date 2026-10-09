# 函数式接口、集合 / UI / 异步回调契约

**日期：** 2026-10-09  
**前置：** `docs/jnative-closures.md`（CallableBinding / MakeCallable / 逃逸+GC）

## 1. 统一函数式接口

| 接口 | 定义位置 | 抽象方法 |
|---|---|---|
| `Runnable` | 编译器内置 SAM | `void run()` |
| `Consumer<T>` | `CollectionsLib` | `void accept(T)` |
| `Supplier<T>` | `CollectionsLib` | `T get()` |
| `Predicate<T>` | `CollectionsLib` | `boolean test(T)` |
| `Function<T,R>` | `CollectionsLib` | `R apply(T)` |
| `IntConsumer` / `IntPredicate` / `IntSupplier` | `CollectionsLib` | 基础类型专用 |
| `IntFunction` / `IntUnaryFunction` | 编译器内置（int→int） | **勿在标准库重复定义** |
| `EventHandler<E>` | `UiLib` | `void handle(E)`（UI 事件 SAM） |

规则：同名 SAM 只允许一个定义；Lambda / 方法引用 / 普通实现类均可作为参数（目标类型由赋值或形参确定）。

## 2. 集合回调（`ArrayList`）

| API | 语义 |
|---|---|
| `forEach(Consumer<T>)` | 按下标顺序调用；结构修改 → `ConcurrentModificationException` |
| `filter(Predicate<T>)` | 新列表，保序；与源独立 |
| `mapValues(Function<T,Object>)` | 新 `ArrayList<Object>`（方法泛型 `map<R>` 暂缓） |

共性：

- `null` 回调 → `NullPointerException`
- 回调异常向调用者传播
- 回调内分配 / GC 安全（闭包 env 为普通堆引用）
- **不做** 完整 Stream、并行流或惰性流水线

## 3. UI 订阅生命周期（`Component`）

| API | 说明 |
|---|---|
| `subscribeAction(EventHandler<ActionEvent>)` | 返回 `UiSubscription` |
| `subscribeChange(EventHandler<UiEvent>)` | 同上 |
| `subscribeClick(Runnable)` | 单槽 click；取消清除 listener |

**`UiSubscription`：**

- `cancel()` 幂等；取消后从列表移除并释放对本订阅所持闭包的引用
- `Component.dispose()` 取消该组件上全部未取消订阅
- 派发时 **snapshot** 当前列表：多订阅者按注册顺序；派发中新增/取消只影响后续事件（当次 snapshot 内已取消者仍可能被调用）
- 回调异常 **不吞掉**，向 `fire*` 调用方传播
- 空 handler → `NPE`

## 4. 异步任务与 Future 回调

| API | 说明 |
|---|---|
| `Async.submit(loop, BlockingSupplier)` | **阻塞适配器**：工作在共享线程池；不是非阻塞网络 |
| `JnFuture.onSuccess` / `onFailure` / `subscribe` | 返回可取消的 `FutureSubscription` |
| `Async.runOnLoop(loop, LoopTask)` | 投递到事件循环就绪队列 |
| `Async.shutdownSharedPool()` | 进程退出前关闭池并 join，避免非守护池线程卡住 `jn_thread_join_remaining` |

### 线程与时机

| 点 | 契约 |
|---|---|
| 完成回调线程 | Future 绑定了 `EventLoop` 时，回调 **入队到该 loop**（在 loop 线程执行）；无 loop 时在完成线程同步调用 |
| 结果已完成再注册 | 立即按上条规则调度；仍受 `FutureSubscription.cancel` 约束 |
| 回调次数 | 每个注册至多一次（`CancellableFutureCallback.claimed`） |
| 回调抛异常 | 不改变 Future 已终态；异常按 loop/`task.run` 路径传播（不回写为 FAILED） |
| 回调返回 Future | **不自动展开**（`onSuccess`/`onFailure` 为 `Consumer`；展开用既有 `flatMap`） |
| `tryCancel` / 订阅 `cancel` | **不** 强制终止已在池中执行的 `BlockingSupplier` |
| `EventLoop.stop` | 粘性 `stopRequested`；`run()` 之前调用也不会被覆盖；退出前再 drain 一次就绪队列 |

跨线程：任务队列持有闭包根引用；完成/取消在 `JnFuture` 的 `Mutex` 下交接回调列表，**在锁外** 调用回调。

## 5. 示例（原生）

| 示例 | 构建 / 运行 |
|---|---|
| `examples/functional/OrderFilterMap.java` | `java -jar target/jnative.jar build …` |
| `examples/functional/UiSubscribeDemo.java` | `JNATIVE_HEADLESS=1` 构建与运行 |
| `examples/functional/AsyncCallbackDemo.java` | 结束后调用 `Async.shutdownSharedPool()` |

**WASM：** `MakeCallable` 仍拒绝 → 依赖 Lambda 的集合/回调 API 在 `wasm32-browser` 上不可用（与闭包里程碑一致）。

## 6. 测试

```bash
mvn -q -Dtest=FunctionalCallbacksEndToEndTest,ClosureEndToEndTest test
```

覆盖：Lambda/方法引用、中文与嵌套、多次 GC、订阅取消/dispose/重复取消、派发中再订阅、异常与 null、完成与取消竞争、loop stop、O0 与默认优化一致。

## 7. 剩余限制

- 无完整 Stream / 并行流 / 惰性 pipeline
- `mapValues` 结果为 `ArrayList<Object>`（非 `ArrayList<R>`）
- `Async.submit` 为阻塞池适配，不是非阻塞 I/O
- WASM 无闭包 / 无上述 Lambda 回调路径
- 捕获对象不因「环境不可变」而线程安全；共享可变状态需调用方同步
