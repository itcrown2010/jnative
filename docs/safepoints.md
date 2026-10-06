# Safepoints (0.9)

STW GC requires every **managed mutator** to stop at a safepoint.

## Poll sites

| Site | Mechanism |
|------|-----------|
| Managed allocation | `jn_safepoint_poll` in `jnative_gc_alloc` |
| Method entry | inserted by LLVM backend after shadow-stack setup |
| Loop backedge | `SafepointPoll` IR before branch to loop header |

Busy loops without allocation still poll on the backedge so GC cannot wait forever.

## IR

```text
SafepointPoll   →   call void @jn_safepoint_poll()
```

The LLVM backend does **not** invent Java safepoint locations; the frontend/IR decides.

## Execution states

```text
MANAGED | NATIVE | SAFEPOINT | TERMINATED
```

- **MANAGED** — may mutate the heap; must reach a poll for STW.
- **NATIVE** — blocking I/O / `join` / `sleep` / mutex wait; GC may proceed; shadow-stack roots still scanned.
- **SAFEPOINT** — parked for (or participating in) STW.
- **TERMINATED** — finished `run()`.

## Managed ↔ Native

Blocking runtime calls (`accept`, `read`, file I/O, `Mutex.lock`, `Thread.join`/`sleep`) wrap with `jn_enter_native` / `jn_leave_native`. Leaving native re-checks for a pending GC request.

## STW handshake

1. Collector takes `collect_mu`, marks itself non-mutator, sets `gc_requested`.
2. Other mutators park at the next poll (`safepoint_parked=1`).
3. Collector waits until no `MANAGED` mutators remain.
4. Scan **all** thread root stacks + TLS exceptions + Thread objects + static refs.
5. Mark / sweep under heap lock.
6. Clear request; resume only `safepoint_parked` threads (not collect-lock waiters).
