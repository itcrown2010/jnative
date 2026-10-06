# Threading (0.9)

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
