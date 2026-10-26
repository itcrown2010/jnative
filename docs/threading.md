# Threading (0.9)

作者：张晓达

JNative 0.9 maps each managed `Thread` to a **real OS thread** (POSIX `pthread` on macOS/Linux).

```text
JNative Thread  →  native OS thread  →  multiple CPU cores
```

Not green threads, coroutines, or JVM threads. No JNI.

## API

```java
class Worker extends Thread {
    public void run() { /* ... */ }
}

Worker w = new Worker();
w.start();   // pthread_create + virtual run()
w.join();    // pthread_join (blocking, enter NATIVE)
Thread.sleep(1000);
Thread t = Thread.currentThread();
long id = t.id();
```

Lifecycle (simplified): `NEW → RUNNING → TERMINATED`. Starting twice throws `IllegalStateException`.

## Runtime model

| Component | Role |
|-----------|------|
| `JNThreadState` (TLS) | Per-thread frame root, exception state, exec state, thread object root |
| Thread registry | All attached managed threads (GC roots / STW / join) |
| `jn_thread_attach_main` | Registers the process main thread |
| `jnative_thread_entry` | Trampoline: root Thread object → virtual `run()` → uncaught handler |

## Synchronization

- `Mutex` — `lock` / `unlock` (ownership check → `IllegalMonitorStateException`)
- `Condition` — `await(Mutex)` / `signal` / `signalAll` (spurious wakeups: await in a loop)
- `volatile` fields — LLVM `atomic` load **acquire** / store **release**
- `AtomicInt` / `AtomicLong` / `AtomicBool` — `get`/`set`/`compareAndSet`/`incrementAndGet`/…

- `Condition.awaitMillis(Mutex, ms)` — timed wait; returns `false` on timeout, `true` when woken
- `AtomicInt` / `AtomicLong` also provide `addAndGet` / `getAndAdd` / `decrementAndGet` / `getAndDecrement` / `getAndSet`; `AtomicBool` provides `getAndSet`

## Thread pools and the concurrency library

`import jnative.concurrent.*;` (or simply use the names — the library is assembled automatically). Native targets only: `wasm32-browser` and the mobile container reject it with a `TARGET_API` diagnostic.

```java
ExecutorService pool = Executors.newFixedThreadPool(4);

TaskFuture<Integer> f = pool.submit(() -> compute());     // Supplier<T>
int v = f.get();                    // blocks; ExecutionException if the task threw
f.get(500);                         // TimeoutException after 500 ms

pool.execute(() -> log());          // fire-and-forget Runnable
pool.shutdown();                    // queued tasks still run
pool.awaitTermination(5000);        // true once every worker exited
```

| Type | Notes |
|------|-------|
| `ExecutorService` | `execute`, `submit` (value), `submitRun` (Runnable), `invokeAll`, `shutdown`, `shutdownNow` (cancels queued futures), `awaitTermination`, `awaitIdle`, `close`, counters (`getActiveCount`, `getQueueSize`, `getCompletedTaskCount`, `getFailedTaskCount`, `getRejectedTaskCount`) |
| `Executors` | `newFixedThreadPool(n)`, `newSingleThreadExecutor()`, `newCpuThreadPool()`, `newDaemonThreadPool(n)`, `newBoundedThreadPool(n, queue)`, `availableProcessors()` |
| `TaskFuture<T>` | `get()`, `get(ms)`, `isDone`, `isFailed`, `isCancelled`, `cancel()` (only before the task started), `awaitDone()` |
| `CountDownLatch` | `countDown`, `getCount`, `waitFor()`, `waitFor(ms)` |
| `Semaphore` | `acquire`, `acquire(n)`, `tryAcquire`, `tryAcquireFor(ms)`, `release`, `availablePermits` |
| `ReentrantLock` | re-entrant `lock`/`unlock`, `tryLock`, `tryLockFor(ms)`, `isHeldByCurrentThread`, `getHoldCount` |
| `CyclicBarrier` | `arriveAndWait()`, optional barrier action |
| `BlockingQueue<T>` | bounded or unbounded; `put`/`take`/`offer`/`poll`/`offerFor`/`pollFor`; `close()` ends consumers cleanly (`take()` then returns `null`) |

Bounded queues: `new ExecutorService(workers, queueCapacity, policy)` with `ThreadPoolExecutor.REJECT_THROW` (`RejectedExecutionException`), `REJECT_CALLER_RUNS`, `REJECT_BLOCK` (back-pressure) or `REJECT_DISCARD`.

Behavior worth knowing:

- A task that throws never kills its worker. For `submit`, the exception is stored in the `TaskFuture`; for `execute`, it is counted (`getFailedTaskCount`), kept (`getLastFailure`) and reported on stderr (`setQuiet(true)` silences the report).
- Pool workers are **non-daemon**: call `shutdown()`/`close()` before `main` returns or the process waits for the workers. `Executors.newDaemonThreadPool` workers do not keep the process alive (queued tasks are dropped at exit).
- `await` is a language keyword, so the blocking methods are named `waitFor` (latch) and `arriveAndWait` (barrier).
- A lambda whose block body only throws cannot infer its `Supplier<T>` type; call a helper method instead (`() -> boom()`).

## Shutdown

All started threads are **non-daemon**. After `main` returns, the runtime calls `jn_thread_join_remaining()` before process exit.

## GC2 / TLAB (0.13)

Each attached managed thread carries a **TLAB** in `ThreadState`. Allocation fast path is thread-local; STW GC retires all TLABs before young copying. Foreign attach/detach acquires/retires a TLAB. See [tlab.md](tlab.md), [gc2.md](gc2.md).

## See also

- `docs/safepoints.md`
- `docs/concurrency-memory-model.md`
- `docs/runtime-locking.md`
- `docs/threading-audit.md`
- `docs/gc2.md`
