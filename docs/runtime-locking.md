# Runtime Lock Order (0.9)

作者：张晓达

To avoid GC / runtime deadlocks, acquire locks in this order only:

```text
1. collect_mu          (jn_gc_collect_lock)     — exclusive collector
2. gc_mu               (STW handshake / TLS exec state)
3. registry_mu         (thread list)
4. heap_mu             (allocation list + counters)
5. user Mutex          (never held across GC wait)
```

## Rules

- **Never** wait for a mutator safepoint while holding a lock that the mutator needs to reach that safepoint (especially `heap_mu` / user mutex).
- `jn_gc_collect_lock` marks the waiter `SAFEPOINT` **before** blocking on `collect_mu`, so other collectors can finish STW.
- `jn_gc_stw_end` only resumes threads with `safepoint_parked=1` — not threads waiting on `collect_mu`.
- Blocking I/O and `pthread_join` run under `NATIVE` so STW does not wait on them.
- User `Mutex.lock` uses trylock + safepoint poll while spinning so GC is not blocked by lock contention.

## I/O

File/socket helpers take `jn_enter_native` around blocking syscalls. They must not hold `gc_mu` / `heap_mu` across those calls.
