# JNative 0.8 — Runtime Stability & Crash Hardening

**Date:** 2026-10-08  
**Platform of record:** macOS arm64 + system clang + lldb  
**Stamp:** `JNATIVE_RUNTIME_0_8_PARTIAL`

---

## Verdict

Short RuntimeStabilitySuite (GC soft stress, threads 2/4/8/16, FFI, exceptions, strings/arrays, network, Debug/Release, LLVM IR verify, ASan/UBSan/TSan when supported) **passed** on this host. A real GC heap-verify bug under live TLAB was **fixed** and covered by regression.

**Not claimed PASS** because required **30-minute combined soak was NOT_RUN** (time/resource gate). Longer soaks must set `JNATIVE_STABILITY_SOAK` and run `scripts/runtime-stability-0.8.sh` — results will not be fabricated.

Audit: `docs/runtime-stability-audit.md`

---

## Architecture Audit

See audit doc. Highlights:

- Default GC2 **moves** young objects (Cheney).
- Exceptions are TLS-based; callbacks clear exceptions and return 0/null to C (no C ABI unwind).
- POSIX threads + STW exist; Windows GC/MT not materialized.
- AsyncTcp remains blocking-adapter (**BLOCKED** as true async).

---

## Crash Classification

Implemented classifier notes in `jnative_crash.h` / harness docs:

NORMAL_EXIT · NONZERO_EXIT · SIGSEGV · SIGABRT · SIGBUS · SIGILL · OS_TERMINATED · SIGNALED · timeout (harness) · deadlock (manual/TSan) · WASM trap (browser path).

`RuntimeCrashDiagnostics` installs async-signal-safe handlers; prefers OS crash reports for full stacks. DWARF via `-g` (default).

---

## GC Findings

| Finding | Severity | Status |
|---------|----------|--------|
| `verify_heap` scanned uninitialized TLAB tails | P1 (false corrupt / hides real issues) | **FIXED** — fill waste before scan; small tails do not advance `g_eden_high` |
| Young gen relocates objects | Design | Documented; FFI must not hold raw managed pointers |
| `FLAG_PINNED` unused | P2 | OPEN — no pin API |
| Every-alloc stress (`GC_STRESS=1`) | Harsh | Soft stress (`soft`) used for gate |

Regression: `GcVerifyTlabRegressionTest`.

---

## Thread Findings

| Test | Result |
|------|--------|
| 2 / 4 / 8 / 16 workers + Mutex | PASS |
| TSan 4-worker (if clang supports) | PASS on this host |
| Deadlock detector | Not implemented |

---

## FFI Findings

| Test | Result |
|------|--------|
| strlen/abs + NativeMemory alloc/free loop | PASS |
| Callback exception boundary (pre-existing) | Documented: cleared, no C throw |
| Managed pointer across safepoint | Still unsafe without pin |

---

## LLVM Findings

| Item | Result |
|------|--------|
| Mid-IR `--verify-ir` | Pre-existing |
| `.ll` verify before link | **ON by default** (`LlvmIrFileVerifier`) |
| Host tool | Homebrew `llvm-as` preferred; else `clang -c` |

---

## Sanitizer Results

| Mode | Supported here | Suite |
|------|----------------|-------|
| ASan | Yes | GC smoke PASS (`detect_leaks=0`) |
| UBSan | Yes | Smoke PASS |
| TSan | Yes | 4-thread PASS (alone, not with ASan) |

ASan/UBSan may combine via `--sanitize=address,undefined`. TSan is separate.

---

## Stress Results

| Suite | Duration | Result |
|-------|----------|--------|
| GC soft stress Debug+Release | short | PASS |
| Thread 2/4/8/16 | short | PASS |
| FFI / Exception / String+Array / Network | short | PASS |
| LLVM verify compile | short | PASS |
| Soak 10 minutes | — | **NOT_RUN** |
| Soak 30 minutes | — | **NOT_RUN** |
| Soak 2 hours | — | **NOT_RUN** |

Enable: `JNATIVE_STABILITY_SOAK=10m|30m|2h ./scripts/runtime-stability-0.8.sh`

---

## Build / CLI (0.8)

```text
jnative Main.java --debug                 # -O0 -g
jnative Main.java --release -O2           # release opt
jnative Main.java --sanitize=address
jnative Main.java --sanitize=undefined
jnative Main.java --sanitize=address,undefined
jnative Main.java --sanitize=thread       # alone
jnative Main.java --verify-llvm           # default on
jnative Main.java --no-verify-llvm
jnative Main.java --no-debug-symbols
```

Env: `JNATIVE_BUILD_ID`, `JNATIVE_NO_CRASH_HANDLER`, `JNATIVE_GC_STRESS=soft|1|…`, `JNATIVE_GC_VERIFY=1`.

---

## Regression Tests

| Test | Purpose |
|------|---------|
| `GcVerifyTlabRegressionTest` | verify after TLAB bump without GC |
| `RuntimeStabilitySuiteTest` | GC/Thread/FFI/Ex/String/Net + sanitizers |
| Existing GcRelocation / GcNativeValidation / Thread / Ffi / Exception | retained |

---

## Known Blockers / Limits

1. **30m / 2h soaks NOT_RUN** on this pass → blocks full PASS stamp.  
2. True AsyncTcp / Linux epoll / Windows GC+sockets / PG SCRAM unchanged.  
3. No object pin API for FFI.  
4. Stability guarantees are **platform- and scenario-scoped** — not absolute crash-freedom.

---

## Acceptance checklist

| Criterion | Result |
|-----------|--------|
| Known reproducible P0 crashes fixed | PASS (TLAB verify path) |
| GC stress | PASS (soft) |
| Thread stress | PASS |
| FFI stress | PASS |
| Exception stress | PASS |
| Sanitizer serious issues | None open on short suite |
| 30 min combined soak | **NOT_RUN** |
| Debug + Release | PASS |

```
JNATIVE_RUNTIME_0_8_PARTIAL
```

**STOP** — no automatic next phase / no ERP work in this phase.
