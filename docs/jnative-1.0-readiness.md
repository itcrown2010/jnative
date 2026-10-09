# JNative 1.0 Readiness Audit

作者：张晓达

**Audit date:** 2026-10-08  
**Auditor role:** evidence-only (source + tests + native e2e + app reports + host runs)  
**Host used for fresh checks:** macOS arm64 (Darwin), `clang`, OpenSSL present  
**Decision:** see §Final Decision  

> This is **not** a feature round. Claims of “done” require tests / e2e / soak / platform / real apps — not README, roadmap, TODO, or class names.

---

## 0. What “JNative 1.0” means (Minimum Product Definition)

**JNative 1.0** means a developer can:

1. Install an SDK on a clean machine  
2. Write JNative source in the **Supported Language Profile**  
3. Compile to a **native** binary (no JRE at runtime)  
4. Use Stable Core: String, Math, Collections, Time, IO/FS, Thread/Locks/Future/Async, HTTP, JSON, SQL (SQLite and/or PostgreSQL per tier), FFI  
5. Build / test / package with the CLI  
6. Deploy on **macOS + Linux + Windows** (matrix VERIFIED)  

**Explicitly out of 1.0 (allowed 1.1+):** Redis, Kafka, RabbitMQ, full ORM, game/3D, full Studio IDE, Argon2 productization, HTTP/2 production, complete Java SE.

**Browser WASM** may be Stable or Beta independently — only if Calendar/browser validation is CI-backed.

---

## 1. Evidence inventory (this audit)

| Bucket | Count / fact |
|---|---|
| Test classes (`src/test/**/*.java`) | **79** |
| Native e2e classes (`e2e/`) | **40** |
| `@Test` methods (approx) | **810** |
| `@Disabled` / `@Ignore` | **0** |
| CI workflows (`.github/`) | **ABSENT** |
| Soak harness (30 min) | **ABSENT** (docs Round2/4: NOT RUN) |
| L4 capabilities | **0** |
| Clean-machine via **repo jar** (macOS arm64) | **PASS** (doctor/new/run/test/build --release) |
| Clean-machine via **released SDK package** | **FAIL** (no SDK distribution) |
| Platforms with CI VERIFIED | **0** |
| Platforms with local e2e VERIFIED | **macOS arm64 only** |

---

## 2. 1.0 Candidate Matrix

Levels: L0 MISSING · L1 WORKS · L2 TESTED · L3 PRODUCTION-CAPABLE · L4 HARDENED  

Release Tier: Stable (≥L3) · Beta (≈L2) · Experimental (L1–L2) · Unsupported  

| Area | Level | Release Tier | 1.0 Required | Status | Blockers | Evidence |
|---|---|---|---|---|---|---|
| Language Profile (declared) | L2 | Beta→Stable target | YES | PARTIAL | Missing bitwise/ternary/switch/enum; casts L0 in profile | `docs/language-support.md`, e2e suites |
| Lexer/Parser/AST | L2–L3 | Stable target | YES | PARTIAL | No fuzz; thin unit suite | `LexerTest`, `ParserTest`, all e2e |
| Semantic / types | L2–L3 | Stable | YES | PARTIAL | No formal typesystem corpus | `TypeSystemTest`, Inheritance* |
| Overload | L2–L3 | Stable | YES | READY | Keep regression forever | `OverloadEndToEndTest`, `MathEndToEndTest` |
| break/continue × finally | L2–L3 | Stable | YES | READY | No labeled break | `LoopControlEndToEndTest` (O0/O2/O3) |
| Exceptions | L2–L3 | Stable | YES | PARTIAL | Stack = symbol only (no file:line) | `ExceptionEndToEndTest` |
| TWR / resources | L2–L3 | Stable | YES | PARTIAL | Leak gate not soak-proven | `ResourceEndToEndTest`, Round4 streams |
| IR / LLVM | L3 | Stable | YES | READY* | *macOS only proven | All native e2e |
| Optimizer O0/O2/O3 identity | L1–L2 | Stable | YES | BLOCKED | Only O0↔O2 ×2 cases | `DifferentialOptTest` |
| GC (gc2) | L2–L3 | Stable | YES | PARTIAL | No 30m soak; no Windows GC | `Gc*EndToEndTest`, `gc-invariants.md` |
| String (+Builder) | L2–L3 | Stable | YES | PARTIAL | Unicode byte-length contract; Builder thin | `StringEndToEndTest`, CollectionsLib |
| Math | L2 | Stable | YES | PARTIAL | Float overload deferred | `MathLib`, `MathEndToEndTest` |
| Collections core | L2 | Stable | YES | PARTIAL | Queue/Deque not implemented by ArrayDeque | `CollectionsEndToEndTest` |
| Time / TZ / DST | L2 | Stable | YES | PARTIAL | London DST thin; cross-OS zoneinfo unproven | `TimeEndToEndTest` |
| IO Streams / ByteBuffer | L2 | Stable | YES | PARTIAL | DirectByteBuffer L0; abstract base stubs | Round4 e2e |
| Filesystem | L2–L3 | Stable | YES | PARTIAL | Multi-OS matrix missing | FileIo + FS Round4 + Explorer |
| Thread / Mutex / Atomic | L2 | Stable | YES | PARTIAL | No 32-thread / 1M-task / soak | `ThreadEndToEndTest` |
| Async / EventLoop | L1–L2 | Stable | YES | BLOCKED | AsyncTcp = pool façade; epoll/IOCP stubs | `AsyncLib`, poll_*.c, Round4 |
| Memory model docs | L1 | Stable | YES | BLOCKED | Claims non-moving; GC2 moves | `memory-model.md` vs `gc-invariants.md` |
| DNS / UDP / TCP / IPv6 | L2–L2+ | Stable | YES | PARTIAL | Single-host | DnsUdp, Network, Ipv6 e2e |
| TLS (default verify) | L2 | Stable | YES | PARTIAL | No Windows TLS; chain depth limited | `TlsNativeEndToEndTest` |
| HTTP/1.1 + security | L2–L3 | Stable | YES | PARTIAL | No multi-OS CI | Http* + HttpSecurity e2e |
| JSON | L2–L3 | Stable | YES | PARTIAL | getLong >INT_MAX native regression missing | JsonLib; scheduler note |
| SQLite | L2–L3 | Beta/Stable | YES (tier) | PARTIAL | Demo-proven; no soak | scheduler/chat + FFI |
| PostgreSQL | L1–L2 | Stable target | YES | BLOCKED | SCRAM unsupported; no live PG e2e | `PgDriverLib` |
| Tx × await same connection | L1–L2 | Stable | YES | BLOCKED | Host-mirror only (`Thread.yield`) | `DatabaseStackTest` |
| FFI + NativeStruct + callback | L2–L3 | Stable | YES | PARTIAL | No cross-ABI CI | NativeDataModel, NativeCallback e2e |
| Package manager | L2–L3 | Stable | YES | PARTIAL | Diamond converge thin; offline unit thin | `Package030SuiteTest` |
| CLI doctor/new/run/test/build | L2 | Stable | YES | PARTIAL | No SDK install path | `ProjectCli` + this audit run |
| CLI fmt/lint/doc/profile/bench | L0 | Beta | NO* | BLOCKED for DX | Missing commands | `CliDispatcher` |
| Clean-machine SDK gate | L0–L1 | Stable | YES | BLOCKED | Jar-from-repo ≠ distribution | No installer/SBOM |
| Fullstack template + `jnative dev` | L1–L2 | Stable | YES | BLOCKED | No `new fullstack-web` template gate | scheduler manual |
| Reproducible builds | L0–L1 | Stable | YES | BLOCKED | No bit-identical gate | — |
| Calendar (real app) | L2 | Stable target | YES | PARTIAL | SQLite default; WASM+RPC+PG not closed | scheduler validation |
| Explorer | L2 | Experimental | NO | NOT VERIFIED soak | No exit/soak record | explorer reports |
| Studio | L2 | Experimental | NO | PARTIAL | Shell, not IDE | Studio e2e |
| WASM browser product | L2 | Beta | OPTIONAL | PARTIAL | No Chromium CI | WasmBrowserTargetTest |
| Cross-platform matrix | L0–L1 | Stable | YES | BLOCKED | No CI; Win/Linux incomplete | native stubs |
| Security gate | L1–L2 | Stable | YES | BLOCKED | PBKDF2 default 500; host-mirror crypto | CryptoLib, security-review |
| Docs gate | L0–L1 | Stable | YES | BLOCKED | README still “0.12 experimental” | README, missing Install/tutorial |
| Release distribution | L0 | Stable | YES | BLOCKED | No SDK tarball/checksums/SBOM | — |

\*fmt/lint not hard-required for binary “hello works”, but required for trustable 1.0 DX surface per charter.

---

## 3. Score (required areas only)

Defined from the **YES** rows in §2 (core Stable 1.0 required), collapsing closely related rows into **37** required capabilities:

| Status | Count |
|---|---|
| **READY** (≥L3 evidence on required host + tests, no open P0 for that area) | **6** |
| **PARTIAL** (L2 / real e2e but gaps) | **22** |
| **BLOCKED** (P0/P1 or missing gate) | **9** |
| **Total required** | **37** |

**Blocker-adjusted readiness:**  
`READY / Total = 6/37 ≈ 16%`  
`(READY + 0.5×PARTIAL) / Total ≈ 17/37 ≈ 46%`  

These are **not** marketing completeness percentages — they are gate counts.

READY examples: overload regression surface, break/continue e2e, IR→LLVM happy path on macOS, TLS default-reject self-signed (posix), HTTP CRLF/redirect strip e2e, FFI sizeof fixture (macOS).

---

## 4. Product Tier recommendation

| Tier | Capabilities |
|---|---|
| **Stable (1.0 target — not yet earned)** | Language profile subset, GC, String, Math, Collections, Time, IO/FS, Thread/Async, HTTP, JSON, SQL, FFI, package/build |
| **Beta** | WASM/WebUI, SQLite-as-default apps, HTTP/2 foundation, Native UI widgets |
| **Experimental** | Explorer, Studio, AsyncTcp “async”, epoll/IOCP stubs, Windows sockets |
| **Unsupported in 1.0** | enum, switch, ternary, bitwise/shift, varargs, records, pattern matching, Redis/Kafka/MySQL, Argon2 product, DirectByteBuffer |

---

## 5. Persona walkthrough (manual)

| Persona | Goal | Result | Notes |
|---|---|---|---|
| A New user | doctor → new → run → test → build --release | **PASS on macOS via repo jar** | Not an SDK install; README still says 0.12 |
| B Backend | HTTP + JSON + PG | **FAIL for modern PG** | SCRAM missing; no live PG e2e; SQLite path works in demos |
| C Systems | FFI + NativeStruct + callback | **PASS (macOS)** | sizeof fixture + foreign-thread callback exist |
| D Fullstack | WASM + RPC + native server | **PARTIAL** | Scheduler: native+SQLite+JS SPA; WASM shell ≠ full Typed RPC calendar |

---

## 6. Trust-loss hotspots (if shipped today)

1. Claiming “1.0” while README says experimental 0.12 and there is **no CI**.  
2. “Cross-platform” while Linux epoll / Windows IOCP+GC+TLS+sockets are stubs.  
3. “Async I/O” while AsyncTcp is a **thread-pool façade**.  
4. “Production Postgres” while SCRAM is unsupported.  
5. Password hashing default **PBKDF2 500 iterations**.  
6. Stack traces without **file:line**.  
7. Docs saying GC is **non-moving** while GC2 moves.  
8. Explorer/Studio marketed as apps without soak / exit verification.

---

## 7. Final Decision

# `JNATIVE_1_0_NOT_READY`

**Reasons (any one would fail the RC gate):**

- P0 count > 0 (see `docs/1.0-release-blockers.md`)  
- Core Stable areas mostly L2, not ≥ L3  
- Clean-machine **distribution** gate FAIL  
- Platform matrix: only macOS arm64 local-VERIFIED; Linux/Windows NOT VERIFIED  
- Security gate FAIL (PBKDF2 default, incomplete native crypto proof)  
- Calendar not closed as Stable full-stack  
- Docs / release artifact gates FAIL  
- No soak / L4 = 0  

**Next step (human decision):** Release Hardening **or** scoped Feature work — **do not** cut `1.0.0-rc.1` until P0 = 0 and gates in §151 of the audit charter pass.

---

## 8. Document set produced by this audit

| File | Purpose |
|---|---|
| `docs/jnative-1.0-readiness.md` | This file |
| `docs/1.0-release-blockers.md` | P0/P1 blockers with evidence |
| `docs/platform-status.md` | Capability master table |
| `docs/architecture-debt.md` | Debt scan + priorities |
| `docs/security-review.md` | Security gate |
| `docs/language-support.md` | Official Supported Language Profile |
