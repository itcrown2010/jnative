# Threading Audit (0.9)

作者：张晓达

Audit of single-thread assumptions before multithreading work.

## (A) Must become thread-local

| Symbol | Location | Notes |
|--------|----------|-------|
| `@jn.gc.frame` | `GcRuntimeEmitter` | Per-thread shadow-stack head |
| `@jn.ex.current` | `ExceptionRuntimeEmitter` | Current exception object |
| `@jn.ex.pending` | same | Pending-exception flag |
| `@jn.ex.returnPending` | same | Finally return completion |
| `@jn.ex.returnValue` | same | Saved return across finally |

## (B) Must be shared + synchronized / STW

| Symbol | Location | Notes |
|--------|----------|-------|
| `@jn.gc.heap_head` | GC | Intrusive heap list |
| `@jn.gc.allocated_bytes` / threshold / stats | GC | Alloc triggers + metrics |
| `@jn.gc.mark_*` | GC | Collector worklist (STW exclusive) |
| `@jn.gc.inited` | GC | Once-init |
| Object `GcHeader.next` / `mark` | heap | STW during collect |
| **Thread registry** (new) | PlatformThread | All ThreadStates for root scan |
| **GC request / safepoint flags** (new) | GcCoordinator | STW handshake |

## (C) Global immutable / init-once OK

- `@meta.*`, vtables, string literals, format constants
- Function bodies of `jnative_*` helpers

## Indirect (no own globals)

`String` / `Array` / `Ffi` / `File` / `Network` / `Bytes` / `Io` — use GC alloc + exception TLS; blocking native calls need MANAGED↔NATIVE transitions.

## Language gaps for 0.9 API

| Gap | Impact |
|-----|--------|
| No static fields | Blocking for `static AtomicInt` / shared counters — **must add** |
| No `break`/`continue` | Workers use flag loops (existing HTTP pattern) |
| No lambdas | `extends Thread` + `run()` override |
| No `synchronized` | Use `Mutex` library/runtime class |

## Lock order (target)

See `docs/runtime-locking.md` after implementation:

1. GC coordinator  
2. Thread registry  
3. Heap allocation  
4. User Mutex (never held across GC wait)
