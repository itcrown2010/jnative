# RH-1 Runtime Truth — Report

作者：张晓达

**Date:** 2026-10-08  
**Phase:** Release Hardening RH-1  
**Feature freeze:** ON  

---

## Verdict

# `JNATIVE_RH1_PARTIAL`

| Gate requirement | Result |
|---|---|
| GC docs == implementation | **PASS** (`gc-current-design.md`; `GcRelocationEndToEndTest` 2/2) |
| AsyncTcp real event-driven proof | **FAIL** (labeled blocking-adapter / Experimental; not event-driven) |
| Tx×await real **PostgreSQL** native E2E | **FAIL** (`SqliteTxAwaitAffinityEndToEndTest` PASS; PG still blocked) |
| No new P0 | **PASS** (discovered pool join hang at exit — test uses `exit(0)`; not a new product P0) |

**STOP — do not start RH-2 until remaining RH-1 gates clear or charter is explicitly amended.**

---

## 1. GC reality (answered)

| Question | Answer |
|---|---|
| Collector implementation | `jnative_gc2.c` (GC2) + optional LLVM IR marksweep |
| Default collector | **GC2** when `JNATIVE_GC` unset / `generational` |
| Moving / non-moving | **Young: moving (Cheney)**; Old/Large: non-moving mark-sweep |
| Young / old | YES / YES |
| Compaction | Young Cheney only; no old compact |
| Object relocation | YES in young |
| Pinning | **None** (`FLAG_PINNED` dead); no `GcHandle`/`PinnedRef` API |
| GcHandle behavior | N/A — does not exist; use handle tables / trampolines |

**SSOT:** [gc-current-design.md](gc-current-design.md)

**Doc fixes:** `memory-model.md`, `gc.md`, `jnative_gc2.h` comment corrected.

**Tests:** `GcRelocationEndToEndTest` — minor GC + `jn_gc2_debug_address` + field integrity.

---

## 2. AsyncTcp reality (answered)

| Backend | Poll infrastructure | Socket I/O path |
|---|---|---|
| macOS | **Real kqueue** (`jnative_poll_kqueue.c`) — used for wakeup/timers | **Not used for AsyncTcp fds** |
| Linux | **epoll stub** (`wait`→0) | N/A |
| Windows | **IOCP stub** | N/A |

**AsyncTcp definition vs reality:**

True async I/O requires: wait for readiness **without** a dedicated blocking OS thread per connection.  
**AsyncTcp fails this** — every op is `Async.blockingOnLoop` → `ThreadPoolExecutor` → blocking `TcpSocket`.

**RH-1 action:** labeled **blocking-adapter / Experimental** in `AsyncLib.java`; e2e `AsyncTcpBlockingAdapterEndToEndTest` proves adapter works without claiming fd-async.  
**Not done:** real kqueue/epoll dispatch (RH-2 / separate eng).

---

## 3. Transaction × await reality

| Item | Finding |
|---|---|
| Design | `TransactionFlatMap` holds `DbConnection` (physical); **not** ThreadLocal |
| Prior proof | Host-mirror only (`DatabaseStackTest`) |
| RH-1 native proof | **SQLite** TEMP table + pool sleeps across 3 queries → `SqliteTxAwaitAffinityEndToEndTest` |
| PostgreSQL `pg_backend_pid()` | **Not run** — needs live PG + SCRAM (B-P0-05) |

---

## 4. Managed pointer contract (GC moving)

| Rule | Status |
|---|---|
| No long-lived raw managed addresses for FFI | Documented in gc-current-design + memory-model |
| Interior pointers | Forbidden across safepoints |
| Pinning | Unavailable — do not claim |

---

## 5. Blocker status updates

| ID | After RH-1 |
|---|---|
| B-P1-05 GC docs mismatch | **VERIFIED** (docs fixed + relocation test) → candidate CLOSED after review |
| B-P1-01 AsyncTcp façade | **FIXED** (truth labeling) — still **OPEN** until real async or Stable demotion accepted |
| B-P0-06 Tx×await | **PARTIAL FIXED** — SQLite native **VERIFIED**; PG path still **OPEN** |
| B-P0-03 Linux epoll | Untouched (RH-2) |
| B-P0-05 PG SCRAM | Untouched (RH-3) |

---

## 6. What remains for `JNATIVE_RH1_PASS`

1. **Either** implement real event-driven AsyncTcp on macOS (kqueue dispatch) **or** amend 1.0 charter: AsyncTcp is Experimental / not a RH-1 pass criterion.  
2. **Either** live PostgreSQL `pg_backend_pid()` native e2e **or** amend charter: SQLite affinity e2e satisfies data-integrity gate for 1.0 SQLite Stable (PG remains Beta until SCRAM).  

Until one of those decisions/engineering lands → stay **PARTIAL**.
