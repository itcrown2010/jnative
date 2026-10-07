# JNative Architecture Debt (1.0 Audit)

**Audit date:** 2026-10-08  
**Companion:** [platform-status.md](platform-status.md) · [1.0-release-blockers.md](1.0-release-blockers.md)  
**Method:** ripgrep + source reading for TODO/FIXME/stub/fake/unsupported; cross-check tests.

---

## Priority legend

| Pri | Meaning | Release blocker? |
|---|---|---|
| **P0** | Corruption, security-critical, platform claim false | **YES** |
| **P1** | Incorrect semantics, major leak/deadlock, core API broken | Usually YES for Stable |
| **P2** | Blocks L3 on foundational modules | No if scoped out |
| **P3** | DX / ecosystem / polish | No |

**Repair order:** P0 → P1 → Core L0→L2→L3 → L4 → new features.

---

## 1. Debt scan counts (approx)

| Pattern | Hits (order of magnitude) | Hotspots |
|---|---|---|
| PARTIAL / stub / not implemented | dozens | `jnative_poll_epoll.c`, `jnative_poll_iocp.c`, `jnative_socket.c` (Windows), `BrowserLib`, `StreamLib` abstract bases, `PgDriverLib` SCRAM |
| TODO/FIXME/HACK in `src/main` | low tens | scattered |
| `@Disabled` tests | **0** | — |
| Soft skips (`Assumptions.assumeTrue`) | many e2e | clang/openssl/sqlite/certs |
| Host-mirror tests (JVM asserting “native” behavior) | several suites | crypto/security/db |

---

## 2. Fake / stub implementations (must not claim Stable)

| Item | What it does | Why dangerous | Pri | Owner |
|---|---|---|---|---|
| Linux epoll | wait→0; no fd register | Async silently idle | P0 | async/native |
| Windows IOCP | stub | Same | P0 | async/native |
| Windows sockets | returns “not implemented” | Net APIs exist, fail | P0 | net |
| Windows GC | **missing files** | Managed apps impossible | P0 | gc |
| AsyncTcp* | `blockingOnLoop` + worker pool | Looks async, isn’t fd-async | P1 | async |
| BrowserLib bodies | return 0/null for host analyze | OK if wasm-only; bad if linked native | P2 | wasm |
| StreamLib abstract read/write | throw “not implemented” | OK if never instantiated | P3 | io |
| HTTP/2 | frames/HPACK foundation | Not a live h2 stack | P2 | http |
| PG SCRAM | explicit throw | Auth fails on real PG | P0 | db |
| PBKDF2 default 500 | “works” under load | Security theater | P0 | crypto |

---

## 3. Dual sources of truth (SSOT violations)

| Topic | Conflicting sources | Pri |
|---|---|---|
| GC moving vs stable pointers | `gc-invariants.md` (GC2 moving) vs `memory-model.md` (“Non-moving”) | P1 |
| Version brand | README **0.12 experimental** vs CLI **1.0-dev** vs pom **0.21** | P1 |
| Future / cancel | Builtin `Future` + `JnFuture`; two `CancellationToken` types | P1 |
| Queue/Deque types | Interfaces exist; `ArrayDeque` does not implement them | P2 |
| language-support vs old platform-status | Prior platform-status claimed break/Math/UDP L0 — **stale** | P2 (docs) |

---

## 4. Dead / legacy paths

| Item | Note | Pri |
|---|---|---|
| Legacy mark-sweep GC path | Documented as `marksweep` alongside GC2 | P2 — document which is default |
| Dual async runtimes / emitters | `AsyncRuntimeEmitter` stub comments | P2 |
| Empty test dirs | `string/`, `rtti/`, `processor/`, `constructor/`, `array/` under tests historically empty | P3 |
| Calendar compile FAIL (merged stdlib) | Round4 note: SchedulerServer parse errors in concat path | P1 for app tier |

---

## 5. LLVM / intrinsic audit (architecture)

| Finding | Result |
|---|---|
| Broad language semantics inside LLVM generator? | Mostly **runtime calls** (`jnative_*`) — acceptable |
| LLVM intrinsics observed | Narrow (e.g. `llvm.memset`) |
| Forbidden domain intrinsics (HTTP/JSON/SQL/UI/Calendar) | **Not found** as LLVM intrinsics — good |
| Meta annotation LLVM intrinsics | Documented NONE (`BuildReport032`) | OK |

**Debt:** keep IR language-level; reject new domain intrinsics in review.

---

## 6. P0 / P1 register (condensed)

Full IDs in `docs/1.0-release-blockers.md`.

| ID | Issue | Blocker? |
|---|---|---|
| B-P0-01 | No CI matrix | YES |
| B-P0-02 | Windows GC/net/TLS incomplete | YES |
| B-P0-03 | Linux epoll stub | YES |
| B-P0-04 | PBKDF2 500 default | YES |
| B-P0-05 | PG SCRAM + no live e2e | YES |
| B-P0-06 | Tx×await native unproven | YES |
| B-P0-07 | No SDK distribution | YES |
| B-P0-08 | No GC soak | YES |
| B-P1-01…14 | Async façade, dual Future, opt diff, stack file:line, docs, JSON long, crypto mirror, SecureRandom e2e, CLI DX, package offline, repro, Calendar/Explorer, brand skew | YES for Stable |

---

## 7. Stale debt entries (closed or superseded since 2026-10-07)

Do **not** keep these as open language P1s:

| Old claim | Current evidence |
|---|---|
| Method overloading rejected | `OverloadEndToEndTest` + Math overload |
| break/continue absent | `LoopControlEndToEndTest` |
| Math L0 | `MathLib` + `MathEndToEndTest` |
| UDP L0 | `DnsUdpEndToEndTest` |
| ByteBuffer missing | Round4 `ByteBufferEndToEndTest` |
| try-with-resources L0 | `ResourceEndToEndTest` |
| Time DST absent | `TimeEndToEndTest` LA DST |
| StringBuilder L0 | class in `CollectionsLib` |

---

## 8. Test health

| Item | Status |
|---|---|
| Flaky retry culture | Not institutionalized; Assumptions skip instead |
| Disabled hiding P0 | None found |
| Random sleeps | Present in some async/network tests — watch flakiness |
| Capability → Tests map | See platform-status; e2e/ has 40 classes |
| Host-mirror false confidence | **Real debt** for crypto/db |

---

## 9. Feature freeze recommendation

Until P0 = 0:

```text
FEATURE FREEZE for new syntax / new DB / new MQ / new UI families
ALLOW: P0/P1 fixes, tests, docs, packaging, perf regressions, CI
```

API freeze for Stable packages only after P0 clear + review.

---

## 10. STOP

Debt catalogued. **Do not auto-fix all blockers in this round.**
