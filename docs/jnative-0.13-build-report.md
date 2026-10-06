# JNative 0.13 Build Report — GC2

## Summary

- Version **0.13**
- Default heap: **generational GC2** (`JNATIVE_GC=generational`)
- Legacy: `JNATIVE_GC=marksweep` (differential / regression)
- Implementation: `jnative_gc2.c` + ThreadState TLAB + LLVM write barriers + updateable root slots

```text
mvn test: PASS
Total tests: 226
Failures: 0
Errors: 0
Skipped: 0
```

| Suite | Result |
|-------|--------|
| GC2 e2e (`Gc2EndToEndTest`, 6) | PASS |
| TLAB / bump / young reclaim | PASS |
| Moving GC roots / cycles / identity | PASS |
| Legacy vs GC2 differential | PASS |
| Major / `System.gc` paths | PASS |
| GC verifier stress (`JNATIVE_GC_VERIFY`) | PASS |
| HTTP GC stress (pinned marksweep) | PASS |
| 0.1–0.12 regression | PASS |
| Native compilation | PASS |

---

## 1. GC2 Architecture

```text
Managed Heap
├── Young (semi-space From/To, TLAB bump)
├── Old (malloc-backed, non-moving mark-sweep)
└── Large Object Space (threshold, same mark-sweep family)
```

Young copying + Old non-moving. Compiler stays on allocation / stores / roots / safepoints; runtime owns TLAB / nursery / copy / promotion / cards.

## 2. Heap Layout

Separate young arenas + old/large linked objects. No single `heap_head` for everything. Address classification for verifier / barriers (`YOUNG` / `OLD` / `LARGE`).

## 3. Young Generation

Semi-space copying (Cheney). Minor GC copies live from → to, swaps spaces, retires TLABs.

## 4. TLAB

Per managed `ThreadState`: `tlabTop` / `tlabEnd` / start. Refill from nursery; flush/waste fillers before minor; retire on thread exit / detach.

## 5. Allocation Fast Path

```text
if top + size <= end: bump; return
else: slow → refill / minor / major / OOM
```

Small objects: young/TLAB. Large (≥ threshold, configurable): old/LOS. Fast path: **no global mutex**.

## 6–8. Young Copying / Forwarding / Roots

`forward(ref)`: null / non-young / already-forwarded / copy-or-promote. Roots are **slots** (`ManagedRef*`) so GC can store new addresses. Shadow stack + static visitor + exception/Thread/callback/handle slots.

## 9–10. Shadow Stack / Stack Objects

Stack-managed objects do not move; their managed ref **fields** are root slots and are updated. Heap closures / Thread objects / callback handles use updateable slots.

## 11–13. Write Barrier / Card Table / Remset

Managed `StoreField` / ref `StoreElement` lower with write-barrier semantics. Old→young dirty cards; minor scans roots + remset. Correctness-first: conservative old rescan still available when needed.

## 14–16. Promotion / Old / Large

Age++ on survive; promote past `JNATIVE_PROMOTION_AGE`. Old/large: non-moving mark-sweep. Promotion failure → major → retry. Large arrays still precise-scanned.

## 17–18. Minor / Major

Minor: STW → flush TLABs → roots + remset → Cheney → promote → swap → reset TLABs. Major / `System.gc()`: full STW mark-sweep over young+old+large. Still Stop-The-World — not low-latency GC.

## 19–21. Multithreading / Foreign / FFI

All mutator TLABs retired under STW. Foreign attach: acquire TLAB; detach: retire. FFI keeps copy-out; no naked managed interior across safepoints. `StrongGcHandle`-style slots for movable refs (architecture).

## 22–23. Optimizer / Verifier

Barriers must survive DCE/move incorrectly; scalar-replaced stores may drop with proof. `JNATIVE_GC_VERIFY=1`: boundaries, generations, no stale from-space, remset checks.

## 24. Stress

`JNATIVE_GC_STRESS=minor|major|all`, small nursery, verify on. Gc2 e2e covers graph / cycle / identity / dead young / differential.

## 25. Benchmarks (local, 2e6 `Point` allocations)

Observable result identical: `4000000000000`

| Mode | Wall (approx) | Collections | Notes |
|------|---------------|-------------|-------|
| `-O0` + marksweep | ~0.75s | 76 | freelist pressure |
| `-O0` + GC2 | ~0.73s | minor 9 / major 0 | `tlab_allocs: 2000001`, `tlab_refills: 2443` |
| `-O2` + GC2 | ~0.55s | 0 | Escape/scalar: `allocated: 1`, zero GC pressure |

```text
-O0 GC2 sample stats:
  minor: 9  major: 0
  tlab_allocs: 2000001
  tlab_refills: 2443
  bytes_copied: 400
  bytes_promoted: 40
```

## 26. Regression

Full suite green including exceptions, arrays, String, FFI, HTTP (marksweep pin for GC stress), threads, atomics, lambda/closure/callback, effects/@NoGC, optimizer, stack allocation.

## 27. Known Limitations

- Minor may conservatively rescan old (correctness > throughput)
- HTTP soak under GC2+stress not yet CI-default (marksweep pin)
- No public pinning API (`FLAG_PINNED` reserved)
- LLVM-inlined TLAB bump not yet (C runtime fast path)
- Weak handles / full card-only remset tuning deferred

## 28. Important Files

- `src/main/resources/native/posix/jnative_gc2.c` / `.h`
- `src/main/resources/native/posix/jnative_mt.c` (TLAB / root slots)
- `GcRuntimeEmitter.java`, `LlvmIrGenerator.java`
- `docs/gc2.md`, `generational-gc.md`, `tlab.md`, `write-barrier.md`, `gc-root-audit.md`, `gc-verifier.md`
- `src/test/java/io/jnative/e2e/Gc2EndToEndTest.java`

## 29. Architecture Acceptance

| Question | Answer |
|----------|--------|
| Every managed thread has a TLAB? | **YES** |
| Normal small alloc bump-pointer? | **YES** |
| Fast path avoids global lock? | **YES** |
| Young GC moves objects? | **YES** |
| All roots updateable? | **YES** |
| GC updates locals / statics / closures / handles? | **YES** |
| Old→young tracked + ref-array barriers? | **YES** |
| Promotion + major reclaim cycles? | **YES** |
| Foreign attached threads allocate safely? | **YES** |
| `@NoGC` allocation-free? | **YES** |
| Optimizer preserves barriers? | **YES** |
| Verifier detects stale pointers? | **YES** |

## 30. 0.14 Readiness

| Question | Answer |
|----------|--------|
| Interfaces without redesigning GC? | **YES** |
| Interface dispatch + closed-world opt? | **YES** |
| Generics erase/specialize into current IR? | **YES** |
| Collections mostly in JNative? | **YES** |
| Stdlib benefits from escape analysis? | **YES** |
| Generic collections + moving GC? | **YES** |
| Begin substantial real Java programs? | **YES** |

## Config

```text
JNATIVE_GC=generational|marksweep
JNATIVE_YOUNG_SIZE
JNATIVE_TLAB_SIZE
JNATIVE_PROMOTION_AGE
JNATIVE_GC_STRESS=minor|major|all
JNATIVE_GC_STATS
JNATIVE_GC_LOG
JNATIVE_GC_VERIFY
```
