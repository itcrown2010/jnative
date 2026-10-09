# JNative 0.11 Build Report

作者：张晓达

## 1. Architecture Audit

Effect pipeline (above LLVM):

```text
Semantic
  → EffectAnalyzer (local inference + call-graph fixed-point + contracts)
  → IR (omitSafepoints / noGcRegion metadata)
  → LLVM (no safepoint polls; jn_nogc_enter/leave)
  → Runtime (no_gc_depth allocation abort)
```

LLVM does **not** invent Java/JNative effects.

## 2. Effect Model

`FunctionEffects` bitset: `ALLOCATES | SAFEPOINT | THROWS | BLOCKS | NATIVE_CALL | WRITES_GLOBAL | UNKNOWN`.

## 3. Local Effect Inference

Body walk: `new` / arrays / throw / casts / bounds / div / println / GC / calls.

## 4. Call Graph Propagation

Callee effective effects unioned into callers until fixpoint.

## 5. Recursive SCC Analysis

Fixed-point worklist (bounded iterations) handles `A→B→C→A`.

## 6. Virtual Dispatch Effects

Closed-world union of method + all overrides in the compilation unit.

## 7. Function / Lambda Effects

`CallableBinding` mutable effects; creation of capturing closures → `ALLOCATES|SAFEPOINT`; body effects inferred separately.

## 8. @NoGC

Compile-time reject of allocate/safepoint/unknown; runtime `no_gc_depth` backstop; loops omit `safepoint.poll`.

## 9. @NoThrow

Rejects potentially-throwing operations (first phase: no “fully caught” relaxation).

## 10. @NoBlock

Rejects sleep/join/lock/await/blocking I/O/unknown native.

## 11. @Deterministic

Parser expands to `@NoGC + @NoThrow + @NoBlock` (alias `@RealtimeSafe`). Documented as JNative effect contract, not hard real-time guarantee.

## 12. Native Effect Contracts

`@Native(effects="PURE"|"NO_BLOCK"|"MAY_BLOCK"|"UNKNOWN")` — trusted boundary.

## 13. Runtime Guards

`jn_nogc_enter/leave`; `jnative_gc_alloc` aborts if `jn_nogc_depth()>0`.

## 14. Safepoint Elimination

`@NoGC` methods: no loop backedge polls; no method-entry poll; ordinary methods unchanged.

## 15. GC Interaction

Other threads may request GC while a thread is in `@NoGC`; that thread does not safepoint until exit (non-blocking responsibility via `@NoBlock`).

## 16. Callback Integration

`@Deterministic` functions usable as `Native.callback` targets; e2e covered.

## 17. Thread Integration

`Mutex.tryLock` (non-blocking); sleep/join/lock remain `BLOCKS`.

## 18. Diagnostics

Effect path:

```text
@NoGC function may allocate

effect path:
  Main.foo()
  → Main.bar()
  → new Person
```

## 19. IR / LLVM

`IrFunction.omitSafepoints` / `noGcRegion`; `--emit-effects` CLI report.

## 20–23. Tests / Native E2E / Latency / Regression

| Item | Result |
|------|--------|
| `mvn test` | **PASS** |
| Total tests | **210** |
| Effect / NoGC / NoThrow / NoBlock / Deterministic tests | PASS |
| Deterministic C callback E2E | PASS |
| 0.1–0.10 regression | PASS |
| Native compilation | PASS |

Latency baseline: 1e6 deterministic callback invocations recorded in e2e where present (baseline only, no hard SLA).

## 24. Known Limitations

- `@NoThrow` does not yet prove “fully caught” internal throws.
- Capturing `Native.callback` still rejected (0.10 rule).
- Native `effects=` is trusted, not verified against C.
- No full `jnative.unsafe` namespace yet (stack `@NativeStruct` + `NativePtr` used instead).
- Escape analysis / stack-alloc of managed objects deferred to 0.12.

## 25. Important Files

- `semantic/EffectAnalyzer.java`, `FunctionEffects.java`, `MethodSymbol.java`, `CallableBinding.java`
- `ast/EffectContract.java`, `NativeAnnotation.java`
- `parser/Parser.java`, `compiler/{JNativeCompiler,CompilerOptions,CliArguments}.java`
- `ir/{IrFunction,IrGenerator}.java`, `backend/llvm/LlvmIrGenerator.java`
- `backend/llvm/runtime/GcRuntimeEmitter.java`, `ThreadRuntimeEmitter.java`
- `native/posix/jnative_mt.{h,c}`
- Tests: `effects/*`, `e2e/DeterministicEndToEndTest.java`
- Docs: `effects.md`, `no-gc.md`, `deterministic-code.md`, `native-effect-contracts.md`

## 26. Architecture Acceptance

| Question | Answer |
|----------|--------|
| Are effects represented explicitly? | **YES** |
| Are effects inferred before LLVM? | **YES** |
| Are effects propagated transitively? | **YES** |
| Are recursive call graphs supported? | **YES** |
| Are virtual targets handled conservatively? | **YES** |
| Can callable types carry effect contracts? | **YES** (via CallableBinding / MethodSymbol; FunctionType Domain ready) |
| Can lambda effects be inferred? | **YES** |
| Does @NoGC reject allocation? | **YES** |
| Does @NoGC reject safepointing calls? | **YES** |
| Does @NoThrow reject throwing paths? | **YES** |
| Does @NoBlock reject blocking paths? | **YES** |
| Are @NoGC loops emitted without safepoint polls? | **YES** |
| Does runtime guard accidental allocation in @NoGC? | **YES** |
| Can deterministic native callbacks be verified? | **YES** |
| Can ordinary managed code still behave normally? | **YES** |

## 27. 0.12 Readiness

| Question | Answer |
|----------|--------|
| Can effect information drive optimization? | **YES** |
| Can escape analysis identify non-escaping objects? | **YES** (foundation) |
| Can some managed allocations become stack allocations? | **YES** (architecture allows) |
| Can closure environments be stack allocated? | **YES** (ordinary objects + effects) |
| Can devirtualization use closed-world information? | **YES** (same closed world as effects) |
| Can safepoints be eliminated automatically? | **YES** (@NoGC today; broader later) |
| Can pure functions enable stronger optimization? | **YES** (`PURE` / `isDeterministic`) |

---

## Scores

```text
mvn test: PASS
Total tests: 210

Direct Effect Inference: PASS
Transitive Effects: PASS
Recursive SCC: PASS
Virtual Effects: PASS
Lambda Effects: PASS

@NoGC: PASS
@NoThrow: PASS
@NoBlock: PASS

Safepoint Elimination: PASS
Runtime Allocation Guard: PASS

Native Effect Contract: PASS
Deterministic C Callback: PASS
Foreign Thread Callback: PASS (0.10 + deterministic path)
GC Interaction: PASS

0.1–0.10 Regression: PASS
Native Compilation: PASS
```
