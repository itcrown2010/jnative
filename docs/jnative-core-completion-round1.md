# JNative Core Completion Report — Round 1

作者：张晓达

**Date:** 2026-10-07 (Round 1) / 2026-10-08 (Round 1.1 Closure)  
**Host evidence:** macOS arm64 (Darwin), clang present  

---

## Round 1.1 Closure

**Verdict:** `JNATIVE_CORE_ROUND1_PASS_PLATFORM_VERIFICATION_PENDING`

| Area | Level | Notes |
|---|---|---|
| Language Core (Supported Profile) | **L2** | See `docs/language-support.md` |
| String (core + split/join) | **L2** | Literal `split`; `join` |
| Math | **L2** | Unchanged from Round 1 |
| Collections (1.0 minimum scope) | **L2** | Scope closed below |

### Round 1.1 deliverables

| Item | Result |
|---|---|
| `break` / `continue` (+ do-while) | PASS |
| nested loops | PASS |
| break/continue × finally | PASS |
| invalid break/continue diagnostics | PASS (`JN1001` / `JN1002`, no compiler crash) |
| `String.split` (literal) | PASS |
| `String.join` | PASS |
| Collections scope definition | PASS (matrix below) |
| Collections native E2E | PASS (`JNATIVE_COLLECTIONS_PASS`) |
| LoopControl native E2E | PASS (`JNATIVE_LOOP_CONTROL_PASS`) |
| `-O0` / `-O2` / `-O3` (Loop / Collections / String join-split) | PASS |
| Math / String / Overload regression | PASS |
| Exception finally regression | PASS (13/13) |
| Second platform runtime | **BLOCKED_BY_ENVIRONMENT** (no Linux/Windows/Docker on host) |

### Test counts

| Metric | Value |
|---|---|
| `@Test` after Round 1 | 748 |
| `@Test` after Round 1.1 | **770** (+22) |
| Focused Round 1.1 core | LoopControl 13 + String 14 + Collections 17 + Math 6 + Overload 2 + Exception 13 = **65** |
| Focused result | **PASS 65 / FAIL 0 / ERR 0** |

### Full `mvn test` (honesty)

| | |
|---|---|
| Total | 770 |
| Focused core (above) | PASS |
| Full suite failures | **48** — overwhelmingly pre-existing UI/Graphics/Editor/Studio (`duplicate class Point/Size/Rect`), TLS version assert, unrelated UI advanced. **Not Round 1.1 language blockers.** |

### Platform matrix

| Target | Round 1.1 |
|---|---|
| macOS arm64 | **PASS / VERIFIED** |
| macOS x64 | NOT VERIFIED |
| Windows x64 | NOT VERIFIED — IMPLEMENTED stubs exist |
| Linux x64 | NOT VERIFIED — **VERIFICATION_ENVIRONMENT_BLOCKED** (no docker/CI runner here) |
| wasm32-browser | NOT VERIFIED |

Code vs environment: language/stdlib work is not blocked; only second-platform **runtime** evidence is missing.

### Collections 1.0 minimum scope matrix

| Interface/Class | Implemented | Tested | Native E2E | Level | Planned |
|---|---|---|---|---|---|
| Iterable | YES | YES | YES | L2 | — |
| Iterator | YES | YES | YES | L2 | — |
| Collection | YES | YES | YES | L2 | — |
| List | YES | YES | YES | L2 | — |
| Set | YES | YES | YES | L2 | — |
| Map | YES | YES | YES | L2 | — |
| Queue | YES (flat iface) | API surface | via ArrayDeque | L2 surface | deep `extends Collection` deferred |
| Deque | YES (flat iface) | API surface | via ArrayDeque | L2 surface | formal `implements` deferred |
| ArrayList | YES | YES | YES | L2 | — |
| HashMap | YES | YES | YES | L2 | — |
| HashSet | YES | YES | YES | L2 | — |
| ArrayDeque | YES | YES | YES | L2 | `offer`; poll/peek aliases deferred (breaks `ArrayDeque<int>`) |
| LinkedList | NO | — | — | L0 | Post-Core-1 |
| LinkedHashMap | NO | — | — | L0 | Post-Core-1 |
| LinkedHashSet | NO | — | — | L0 | Post-Core-1 |
| PriorityQueue | NO | — | — | L0 | Post-Core-1 |

### P0 this round

| ID | Issue | Status |
|---|---|---|
| P0-FINALLY-CFG | Finally epilogue break/continue arms left unterminated LLVM blocks when no loops | **FIXED** |
| (none open in Round 1.1 scope) | | |

### P1 notes

| ID | Issue | Status |
|---|---|---|
| P1-DEQUE-IMPLEMENTS | `ArrayDeque implements Deque` / `poll`/`peek` break `ArrayDeque<int>` itable/codegen | OPEN (documented) |
| P1-QUEUE-EXTENDS | `Queue extends Collection` breaks primitive specialization include | OPEN — flat interfaces used |
| Platform second target | No Linux/Windows execution host | ENVIRONMENT |

### Architecture

- Break uses **BreakTarget / LoopFrame** (not LLVM-special-cased AST).
- Abrupt completion kinds in TLS: return=1, break=2, continue=3.
- No test-only bypasses.

### Explorer

Separate desktop regression issue (prior ~110s run, unknown termination). **Not touched** in Round 1.1.

---

## Round 1 summary (historical)

Initial Round 1 ended `JNATIVE_CORE_ROUND1_PARTIAL` (Language Core L1; missing break/continue/split/join/scope).

Round 1 P0s fixed: overload semantic + IR, MathLib restore, Darwin NaN predicates.

---

## Final status

```
JNATIVE_CORE_ROUND1_PASS_PLATFORM_VERIFICATION_PENDING
```

**STOP.** Await next round (Time / GC / … only when instructed).
