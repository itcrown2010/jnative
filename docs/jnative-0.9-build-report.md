# JNative 0.9 Build Report

作者：张晓达

## 1. Architecture Audit

Completed in `docs/threading-audit.md`: TLS for frame/exception state; locked heap + STW coordinator; static fields added for shared counters.

## 2–7. Native Thread Model

POSIX `jnative_mt.c`: `JNThreadState` TLS, registry, `pthread_create`/`join`, main attach, `PlatformMutex`/`Condition` wrappers. Encapsulated away from the Java compiler frontend.

## 8–15. Thread API / Lifecycle

Builtin `Thread` with virtual `run()`, `start`/`join`/`sleep`/`currentThread`/`id`, start-twice → `IllegalStateException`, non-daemon join-remaining on process exit.

## 16–31. STW GC Upgrade

`jn_gc_collect_lock` + STW + multi-thread root scan; global allocation lock; MANAGED/NATIVE transitions on blocking I/O.

## 32–33. TLS Exceptions

`jn_tls_ex_*`; worker uncaught prints and terminates that thread only.

## 34–40. Sync

`Mutex` / `Condition` with ownership checks; await-in-loop documented.

## 41–46. volatile / Atomics

Acquire/release volatile field accesses; Atomic* via LLVM `atomicrmw`/`cmpxchg` (seq_cst).

## 47–76. Acceptance

`ThreadEndToEndTest` (start/join/sleep/currentThread/mutex/atomics/producer-consumer). Prior HTTP/Network/File/GC/Exception/FFI E2E remain green.

## 77–81. IR / Memory Model

`SafepointPoll`, static load/store, atomic IR ops; `docs/concurrency-memory-model.md`.

## 85–90. Regression

`mvn test`: **174 tests, 0 failures, 0 errors**.

## 92. Documentation

Added: `threading.md`, `safepoints.md`, `concurrency-memory-model.md`, `runtime-locking.md`, `threading-audit.md`. Updated: `gc.md`, `language-support.md`, README.

## Scores

| Item | Result |
|------|--------|
| mvn test | PASS |
| Total tests | 174 |
| Thread tests | PASS (`ThreadEndToEndTest` ×6) |
| Native OS Threads | PASS |
| Thread.start/join/sleep | PASS |
| Thread-local exceptions | PASS (TLS) |
| Safepoints / all-thread roots | PASS |
| Mutex / Condition / volatile / Atomic* | PASS |
| Producer/Consumer | PASS |
| 0.1–0.8.3 regression | PASS |
| Native Compilation | PASS |

## Architecture Acceptance

| Question | Answer |
|----------|--------|
| Every managed thread has ThreadState? | YES |
| currentException thread-local? | YES |
| Per-thread root stack? | YES |
| GC scans all threads? | YES |
| STW stops mutators? | YES |
| Loop backedge safepoints? | YES |
| Blocking native + GC? | YES |
| Thread start/term during GC safe? | YES |
| Heap alloc thread-safe? | YES |
| Cross-thread refs survive GC? | YES |
| Real OS threads? | YES |
| join visibility? | YES |
| Mutex / Condition / volatile / Atomic? | YES |

## 0.10 Readiness

| Question | Answer |
|----------|--------|
| Callbacks attach native threads? | YES (attach API pattern exists; public callback attach TBD) |
| `@NoGC` regions enforceable later? | YES |
| Function pointers enter later? | YES |
| Thread pools as library code? | YES |
| Async networking later? | YES |
| Concurrent/generational GC later? | YES |
| Self-host concurrent server later? | YES |

## Known Limitations

- No Windows threading backend yet (POSIX path).
- No daemon threads; no `synchronized`; no full JMM.
- Global allocation lock (no TLAB).
- STW only (not concurrent GC).

## Important Files

- `src/main/resources/native/posix/jnative_mt.c` / `.h`
- `ThreadRuntimeEmitter.java`, `GcRuntimeEmitter.java`, `ExceptionRuntimeEmitter.java`
- `SemanticAnalyzer.installThreadBuiltins`, `IrGenerator` safepoints/static/atomics
- `docs/threading*.md`, `docs/safepoints.md`, `docs/runtime-locking.md`
