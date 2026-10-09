# JNative Platform Status — 1.0 Readiness Audit

作者：张晓达

**Audit date:** 2026-10-08  
**Evidence basis:** source, `src/test` (79 classes, ~810 `@Test`, 40 native e2e), app validation reports, Round1–4 docs, host runs on **macOS arm64**.  
**Not evidence:** README marketing, roadmaps, TODOs, class names alone.

**Decision:** [`JNATIVE_1_0_NOT_READY`](jnative-1.0-readiness.md) — see [1.0-release-blockers.md](1.0-release-blockers.md).  
**RH-1:** [`JNATIVE_RH1_PARTIAL`](rh1-runtime-truth.md) — GC truth PASS; AsyncTcp not event-driven; Tx×await SQLite PASS / PG OPEN.

---

## Legend

| Level | Meaning |
|---|---|
| **L0** | MISSING — absent, stub, or hard-reject |
| **L1** | WORKS — happy path source→native→run |
| **L2** | TESTED — unit + negative + native e2e / edge |
| **L3** | PRODUCTION-CAPABLE — real app + failure + lifecycle + sanity |
| **L4** | HARDENED — stress + soak + cross-platform + failure injection |

Platform cells: **VERIFIED** · **PARTIAL** · **NOT VERIFIED** · **UNSUPPORTED**

Columns: Capability · Level · Implemented · Tested · Native E2E · Stress · Soak · Platforms · Real App · Known Issues · Blocker

---

## Inventory

| Bucket | Fact |
|---|---|
| Tests | 79 classes / ~810 methods / 40 e2e |
| `@Disabled` | 0 |
| CI (`.github`) | **ABSENT** |
| L4 areas | **0** |
| 30m soak | **NOT RUN** |
| Clean machine (repo jar, macOS arm64) | doctor/new/run/test/build --release **PASS** |
| Clean machine (SDK distro) | **FAIL** (no SDK) |

---

## Platform matrix

| Target | Status | Evidence |
|---|---|---|
| macOS arm64 | **VERIFIED** (local e2e host) | All Round e2e; this audit jar session |
| macOS x64 | **NOT VERIFIED** | No CI / no report |
| Linux x64 | **NOT VERIFIED** | epoll **PARTIAL stub** |
| Linux arm64 | **NOT VERIFIED** | — |
| Windows x64 | **NOT VERIFIED** | No GC; sockets/IOCP/TLS incomplete |
| wasm32-browser | **PARTIAL** | Compile + Node smoke; no Chromium CI |

---

## Master capability table

### A. Language / Compiler

| Capability | Level | Impl | Tested | Native E2E | Stress | Soak | Platforms | Real App | Known Issues | Blocker |
|---|---|---|---|---|---|---|---|---|---|---|
| Lexer | L2–L3 | Y | Y | via all | N | N | macOS | Y | No fuzz | — |
| Parser | L2–L3 | Y | Y | via all | N | N | macOS | Y | No fuzz | — |
| AST | L3 | Y | Y | via all | N | N | macOS | Y | — | — |
| Semantic / types | L2–L3 | Y | Y | Y | N | N | macOS | Y | No typesystem corpus | — |
| Overload | L2–L3 | Y | Y | Y | N | N | macOS | Y | Keep regression | — |
| IR | L3 | Y | Y | Y | N | N | macOS | Y | — | — |
| LLVM lowering | L3 | Y | Y | Y | N | N | macOS | Y | Multi-OS unproven | — |
| Optimizer | L1–L2 | Y | thin | thin | N | N | macOS | partial | O0/O2/O3 identity missing | **P1** |
| class/iface/abstract/inherit | L2–L3 | Y | Y | Y | N | N | macOS | Y | No default iface methods | — |
| generics | L2–L3 | Y | Y | Y | N | N | macOS | Y | Wildcards untested | — |
| lambda / method ref | L2–L3 | Y | Y | Y | N | N | macOS | Y | — | — |
| break/continue | L2–L3 | Y | Y | Y | N | N | macOS | Y | No labels | — |
| try/catch/finally/throw | L2–L3 | Y | Y | Y | N | N | macOS | Y | Stack no file:line | **P1** |
| TWR | L2–L3 | Y | Y | Y | N | N | macOS | Y | — | — |
| enum | L0 | N | N | N | N | N | — | N | — | Profile exclude |
| switch | L0 | N | N | N | N | N | — | N | — | Profile exclude |
| ternary | L0 | N | N | N | N | N | — | N | — | Profile exclude |
| bitwise/shift | L0 | N | N | N | N | N | — | N | — | Profile exclude |
| varargs | L0 | N | N | N | N | N | — | N | — | Profile exclude |
| records / patterns | L0 | N | N | N | N | N | — | N | — | Profile exclude |
| annotations / meta | L2 | Y | Y | partial | N | N | macOS | scheduler | — | — |
| Invalid source diagnostics | L2 | Y | partial | Y | N | N | macOS | — | No fuzz; crash risk unknown | — |

### B. Runtime / GC / Resources

| Capability | Level | Impl | Tested | Native E2E | Stress | Soak | Platforms | Real App | Known Issues | Blocker |
|---|---|---|---|---|---|---|---|---|---|---|
| Object model / vtable / RTTI | L2–L3 | Y | Y | Y | N | N | macOS | Y | rtti suite thin | — |
| GC gc2 | L2–L3 | Y | Y | Y | short | **N** | macOS only | Y | No Win GC; no 30m | **P0** |
| GC roots (thread/async/FFI) | L2–L3 | Y | partial | partial | partial | N | macOS | partial | Matrix incomplete | **P0** |
| Exceptions × GC | L2 | Y | Y | Y | N | N | macOS | — | — | — |
| Stack trace file:line | L1–L2 | partial | Y | Y | N | N | macOS/Linux symbols | — | Symbol only | **P1** |
| AutoCloseable / Owned / GcHandle | L2–L3 | Y | Y | Y | N | N | macOS | Y | Leak soak missing | — |
| Resource baseline restore | L1–L2 | partial | smoke | smoke | N | N | macOS | — | No gate | — |

### C. Stdlib

| Capability | Level | Impl | Tested | Native E2E | Stress | Soak | Platforms | Real App | Known Issues | Blocker |
|---|---|---|---|---|---|---|---|---|---|---|
| String | L2–L3 | Y | Y | Y | N | N | macOS | Y | length=UTF-8 bytes | — |
| StringBuilder | L2 | Y | partial | via coll | N | N | macOS | Y | Not full Java | — |
| Math | L2 | Y | Y | Y | N | N | macOS | — | Float overload deferred | — |
| Collections core | L2 | Y | Y | Y | N | N | macOS | Y | Queue/Deque not wired | — |
| LinkedList/PriorityQueue/… | L0 | N | N | N | N | N | — | N | Defer 1.1 | — |
| Time / TZ / DST | L2 | Y | Y | Y | N | N | macOS | Calendar | London DST thin | — |
| IO Streams / ByteBuffer | L2 | Y | Y | Y | N | N | macOS | — | DirectBB L0 | — |
| Filesystem | L2–L3 | Y | Y | Y | N | N | macOS (+Win FS code) | Explorer | Multi-OS NOT VERIFIED | — |
| Regex / UUID / Base64 | L1–L2 | partial | partial | partial | N | N | macOS | partial | Uneven | — |
| Process | L2–L3 | Y | Y | Studio | N | N | macOS | Studio | Win unproven | — |
| Logging / Config | L1–L2 | Y | partial | partial | N | N | macOS | apps | Logger thin | — |
| SecureRandom | L2 code / L1 proof | Y | host | **N** | N | N | code multi-OS | Auth | Need native e2e | **P1** |
| PBKDF2 / Auth KDF | L1 | Y | host | demo | N | N | macOS | Scheduler | Default **500** iters | **P0** |

### D. Concurrency

| Capability | Level | Impl | Tested | Native E2E | Stress | Soak | Platforms | Real App | Known Issues | Blocker |
|---|---|---|---|---|---|---|---|---|---|---|
| Thread / Mutex / Atomic | L2 | Y | Y | Y | thin | N | macOS | Explorer | No 32-thread | — |
| ThreadPool / Future | L2 | Y | thin | thin | N | N | macOS | Y | Dual Future | **P1** |
| Async/Await / EventLoop | L1–L2 | Y | Y | thin | N | N | kqueue only | Y | epoll/IOCP stub | **P0** |
| AsyncTcp | L1 | façade | Y | Y | idle100 | N | macOS | — | Not kqueue I/O | **P1** |
| Memory model | L1 docs | Y | — | — | N | N | — | — | Docs contradict GC2 | **P1** |
| 100k/1M tasks / 30m conc soak | L0 | N | N | N | N | N | — | N | Missing gates | **P0/P1** |

### E. Networking

| Capability | Level | Impl | Tested | Native E2E | Stress | Soak | Platforms | Real App | Known Issues | Blocker |
|---|---|---|---|---|---|---|---|---|---|---|
| DNS | L2 | Y | Y | Y | N | N | macOS | — | — | — |
| UDP | L2 | Y | Y | Y | N | N | macOS | — | — | — |
| TCP IPv4 | L2–L3 | Y | Y | Y | partial | N | macOS | HTTP apps | — | — |
| IPv6 | L2 | Y | Y | Y | N | N | macOS ::1 | — | Skipable | — |
| TLS | L2 | Y | Y | Y | N | N | posix | HTTPS e2e | No Win | **P0** Win |
| HTTP/1.1 | L2–L3 | Y | Y | Y | N | N | macOS | Scheduler/chat | — | — |
| HTTP security | L2–L3 | Y | Y | Y | N | N | macOS | — | — | — |
| HTTP/2 | L0–L1 | partial | host | N | N | N | — | N | Foundation only | — |
| WebSocket | L2 | Y | host/partial | partial | N | N | macOS | chat | Mirror risk | — |

### F. Data / Serialization

| Capability | Level | Impl | Tested | Native E2E | Stress | Soak | Platforms | Real App | Known Issues | Blocker |
|---|---|---|---|---|---|---|---|---|---|---|
| JSON | L2–L3 | Y | Y | partial | N | N | macOS | Scheduler | getLong>INT_MAX untested | **P1** |
| Generated codec | L2–L3 | Y | meta | partial | N | N | macOS | — | No runtime reflection path (good) | — |
| SQL core / pool | L2 | Y | host | partial | N | N | macOS | Scheduler | — | — |
| SQLite | L2–L3 | Y | Y | demos | N | N | macOS | Scheduler/chat | — | — |
| PostgreSQL | L1–L2 | Y | host | **N live** | N | N | — | optional path | SCRAM missing | **P0** |
| Tx × await affinity | L1 | claimed | host mirror | **N** | N | N | — | — | Integrity gate | **P0** |

### G. FFI

| Capability | Level | Impl | Tested | Native E2E | Stress | Soak | Platforms | Real App | Known Issues | Blocker |
|---|---|---|---|---|---|---|---|---|---|---|
| C ABI / primitives / pointers | L2–L3 | Y | Y | Y | N | N | macOS | SQLite | Multi-ABI CI missing | — |
| NativeStruct sizeof/align | L2–L3 | Y | Y | Y | N | N | macOS | — | Fixture match | — |
| Callback + foreign thread | L2–L3 | Y | Y | Y | N | N | macOS | — | — | — |
| GcHandle | L2 | Y | partial | partial | N | N | macOS | — | Moving GC docs | **P1** |

### H. Web / WASM / Desktop / Apps

| Capability | Level | Impl | Tested | Native E2E | Stress | Soak | Platforms | Real App | Known Issues | Blocker |
|---|---|---|---|---|---|---|---|---|---|---|
| wasm32-browser | L2–L3 | Y | Y | Node | N | N | wasm | Scheduler shell | No Chromium CI | — |
| WebUI / reactive | L2 | Y | Y | partial | N | N | wasm | demos | — | — |
| Unsupported browser APIs diagnostic | L1–L2 | partial | partial | — | N | N | — | — | Must be compile-time | — |
| Native UI | L2–L3 | Y | Y | Y | N | N | macOS (+Win UI code) | Explorer/Notepad | Win NOT VERIFIED | — |
| Calendar/Scheduler | L2 | Y | logic | demos | N | N | macOS | **YES** | PG/WASM/RPC PARTIAL | **P1** |
| Explorer | L2 | Y | manual | compile/run | N | **N** | macOS | **YES** | Soak/exit **NOT VERIFIED** | — |
| Studio | L2 | Y | Y | headless | N | N | macOS | shell | Not IDE | — |

### I. Tooling / Package / Release

| Capability | Level | Impl | Tested | Native E2E | Stress | Soak | Platforms | Real App | Known Issues | Blocker |
|---|---|---|---|---|---|---|---|---|---|---|
| doctor/new/run/test/build | L2 | Y | manual | jar session | N | N | macOS | hello | No SDK path | **P0** dist |
| fmt/lint/doc/profile/bench | L0 | N | N | N | N | N | — | N | Missing | **P1** DX |
| Package add/publish/cache | L2–L3 | Y | Y | — | package | N | macOS | — | Diamond/offline thin | — |
| Package security | L2 | Y | Y | — | N | N | — | — | — | — |
| Offline build | L1–L2 | Y | thin | — | N | N | — | — | — | **P1** |
| Reproducible build | L0–L1 | N | N | N | N | N | — | N | timestamps/paths unknown | **P1** |
| SDK distribution / SBOM | L0 | N | N | N | N | N | — | N | — | **P0** |
| Docs gate | L0–L1 | partial | — | — | N | N | — | — | README 0.12 vs 1.0-dev | **P1** |

---

## Answers to the six audit questions

### 1. What is truly mature?

On **macOS arm64**, with real native e2e:

- Compiler pipeline happy path (source→LLVM→clang→run)  
- OO subset, exceptions, break/continue, overload, generics/lambda (profile)  
- GC short stress + cycles (not soak)  
- Collections/Time/FS/TCP/HTTP/TLS(posix)/FFI sizeof+callback  
- Package integrity basics  
- Hello CLI loop via **repo jar**

Ceiling: mostly **L2–L3**, **never L4**.

### 2. Implemented but under-verified?

- PostgreSQL driver (no live e2e, no SCRAM)  
- AsyncTcp “async” (pool façade)  
- SecureRandom (CSPRNG code, no native e2e)  
- JSON getLong fix (no >INT_MAX native regression)  
- Explorer/Studio (compile/run demos, no soak)  
- Windows FS/UI code (no Windows CI)  
- Optimizer tiers (thin differential)  
- WASM Calendar (screenshots/manual)

### 3. P0 / P1?

See `docs/1.0-release-blockers.md`: **8 P0**, **14 P1**.

### 4. Platforms not actually run?

Linux x64/arm64, Windows x64, macOS x64, Chromium wasm — **NOT VERIFIED**. Only macOS arm64 local.

### 5. Insufficient for 1.0?

Cross-platform runtime, async I/O honesty, PG auth, GC soak, SDK distribution, security KDF, docs/brand, CI, Calendar Stable closure, stack traces, memory-model truth.

### 6. Where would users lose trust if shipped today?

See readiness §6 — false cross-platform, fake async, unsafe password defaults, SCRAM/PG failures, doc contradictions, experimental README vs “1.0”.

---

## Score snapshot

| Metric | Value |
|---|---|
| Required capabilities | 37 |
| READY | 6 |
| PARTIAL | 22 |
| BLOCKED | 9 |
| P0 | 8 |
| P1 | 14 |
| Platforms VERIFIED | 1 (macOS arm64 local) |
| Native e2e classes | 40 |
| Stress (short GC/net) | YES (limited) |
| Soak 30m | **0** |
| Real apps | Calendar L2 · Explorer L2 · Studio L2 |
| Clean-machine (jar) | PASS macOS |
| Clean-machine (SDK) | FAIL |

---

## Final stamp

# `JNATIVE_1_0_NOT_READY`
