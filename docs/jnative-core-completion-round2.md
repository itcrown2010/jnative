# JNative Core Completion Report — Round 2

**Theme:** Runtime Integrity  
**Date:** 2026-10-08  
**Host:** macOS arm64  

## Baseline

| Metric | Value |
|---|---|
| Tests before Round 2 | 770 |
| Full suite FAIL (pre-existing UI/Studio/TLS noise) | ~45 |
| Round 1 open P0 in scope | none — proceeded |

## Levels (Supported Profile)

| Area | Level | Evidence |
|---|---|---|
| Time | **L2** | `TimeLib` + IANA via platform zoneinfo; `TimeEndToEndTest` (6) incl. DST LA; `-O0/O2/O3` |
| Exception | **L2+** | cause/`getCause`; stack symbols on uncaught; finally × break/continue; `ExceptionEndToEndTest` (15) |
| GC | **L2+** | `--gc-stress`; heap verify; `GcNativeValidationEndToEndTest`; existing Gc/Gc2 e2e |
| Resource Model | **L2** | `AutoCloseable` + TWR; reverse close; four-way intersection; `ResourceEndToEndTest` (4) |

## Docs added

- `docs/gc-invariants.md`
- `docs/resource-ownership.md`
- `docs/time-semantics.md`
- `docs/language-support.md` (TWR SUPPORTED)

## Platform

| Target | Status |
|---|---|
| macOS arm64 | VERIFIED |
| macOS x64 / Windows / Linux / WASM | NOT VERIFIED / ENVIRONMENT_BLOCKED |

## Soak

30-minute GC soak: **NOT COMPLETED** in this turn (short stress covered by e2e). Treat as P1 follow-up for L3/L4.

## Verdict

```
JNATIVE_CORE_ROUND2_PASS_PLATFORM_VERIFICATION_PENDING
```

Blockers for full PASS: second-platform runtime verification; 30-minute soak evidence; ResourceTracker remains counter-level (not full OS handle registry).
