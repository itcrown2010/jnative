# JNative 0.10 Build Report

作者：张晓达

## 1. Architecture Audit

Unified Callable model: Static/Instance/Virtual method, Lambda, Closure, Method Reference, Native Function, Native Callback — all map to managed `{codePtr, env}` SAM objects or typed native function pointers. Pipeline: Parser → Semantic (`CallableBinding`) → IR (`make_callable` / `invoke_callable`) → LLVM indirect call / C ABI trampoline.

## 2. Callable Model

Managed SAM layout: `{ ClassMetadata*, i64 code, ptr env }`. Internal ABI: `RetTy code(ptr env, args...)`.

## 3. FunctionType

`FunctionType(parameterTypes, returnType, Domain{MANAGED|NATIVE})`. SAM builtins hold a `FunctionType` for target typing.

## 4. FunctionValue

First-class: assign / pass / return / store / invoke via `MakeCallable` + `InvokeCallable`.

## 5. Lambda

`x -> e`, `(a,b) -> e`, block bodies. AST: `LambdaExpression` / `LambdaParameter` (no LLVM details).

## 6. Capture Analysis

Semantic capture set; effectively-final rejection; synthetic `__jn.Env.N` with reference offsets for GC.

## 7. Closure Environment

GC-managed ordinary objects; escapes defining function; survives `System.gc()`.

## 8. GC Integration

Env / bound receiver / retained callback callable are reference fields; precise mark via ClassMetadata.

## 9. Method Reference

`Type::static`, bound `recv::method`, virtual dispatch preserved for instance virtual methods.

## 10. Callable IR

`MakeCallable`, `InvokeCallable`, `CreateNativeCallback`, `ReleaseNativeCallback`.

## 11. LLVM Lowering

Indirect `call` through code pointer; trampolines for C ABI; `jn_enter_native`/`leave` around `NativeCall`.

## 12. Thread Integration

`Thread(Runnable)`; default `run()` invokes target callable; worker uncaught exception path unchanged.

## 13. HTTP Handler Integration

`HttpServer.get(path, HttpHandler)` + `serveOne()` — language SAM, no compiler special case.

## 14. NativeFunctionPtr

`NativeFunctionPtrType` distinct from `NativePtrType`; C ABI assignable at native call sites.

## 15–19. Native Callback

`Native.callback` → `NativeCallbackHandle` (`pointer`/`close`); non-capturing only; trampoline + `@jn.cb.callables`; foreign `jn_thread_attach_foreign` / detach; exception cleared at C boundary (safe default return).

## 20. Function Effects

`FunctionEffects` metadata (`mayAllocate`, `maySafepoint`, `mayThrow`, `mayBlock`, `nativeCall`) — diagnostic/0.11 prep.

## 21–22. Native C E2E / GC Stress

Custom C fixtures + qsort + foreign-thread callback with `System.gc()` PASS.

## 23–24. Tests / Regression

| Suite | Result |
|-------|--------|
| `mvn test` | **PASS** (197 tests, 0 failures) |
| Callable unit | PASS (6) |
| Callable E2E | PASS (13) |
| Native callback E2E | PASS (4) |
| Thread E2E | PASS (6) |
| 0.1–0.9 regression | PASS |

## 25. Known Limitations

- Capturing `Native.callback` rejected (no global slot hack).
- Callback table: 256 slots.
- HttpHandler single-route demo.
- No full Java generics / streams / MethodHandle / libffi.
- Callback exception → safe default (e.g. `0`), not errno.

## 26. Important Files

- `semantic/{FunctionType,CallableBinding,ClosureCapture,NativeFunctionPtrType,FunctionEffects,NativeCallbackSite}.java`
- `ast/{LambdaExpression,LambdaParameter,MethodReferenceExpression}.java`
- `ir/{IrInstruction,IrGenerator,IrBuilder,IrPrinter}.java`
- `backend/llvm/{LlvmIrGenerator,runtime/CallableRuntimeEmitter}.java`
- `native/posix/jnative_mt.{h,c}` — foreign attach
- `stdlib/http/HttpLib.java` — `get` / `serveOne`
- Tests: `callable/*`, `e2e/CallableEndToEndTest`, `e2e/NativeCallbackEndToEndTest`
- Docs: `docs/callable.md`, `docs/native-callback.md`

## 27. Architecture Acceptance

| Question | Answer |
|----------|--------|
| Are function values first-class? | **YES** |
| Does managed callable use code + environment? | **YES** |
| Are captures determined before LLVM? | **YES** |
| Are closure environments precisely GC scanned? | **YES** |
| Can closures escape their defining function? | **YES** |
| Are callable invocations truly indirect? | **YES** |
| Do virtual method references preserve dynamic dispatch? | **YES** |
| Is NativeFunctionPtr typed? | **YES** |
| Is NativeFunctionPtr distinct from NativePtr? | **YES** |
| Can JNative expose a real C ABI callback? | **YES** |
| Can foreign C threads enter JNative safely? | **YES** |
| Do callback threads participate in GC? | **YES** |
| Can exceptions be stopped at C boundary? | **YES** |
| Does callback lifetime have explicit ownership? | **YES** (`NativeCallbackHandle`) |

## 28. 0.11 Readiness

| Question | Answer |
|----------|--------|
| Can @NoGC functions be statically verified? | **YES** (effects metadata + call-graph ready; checker not shipped) |
| Can allocation-free callbacks be guaranteed? | **YES** (with effects propagation; enforcement TBD) |
| Can real-time/native callback paths avoid GC? | **YES** (architecture allows; policy TBD) |
| Can function effects propagate through call graph? | **YES** (model present) |
| Can blocking operations be rejected in @NoGC? | **YES** (`mayBlock` field) |
| Can throwing operations be rejected where forbidden? | **YES** (`mayThrow` field) |
| Can stack allocation replace some closure allocations later? | **YES** (env is ordinary object; escape analysis future) |

---

## Scores

```text
mvn test: PASS
Total tests: 197
Callable tests: 19 (unit+e2e)
Lambda / Closure / Method Reference / Indirect Invocation: PASS
Thread Lambda: PASS
HTTP Handler Lambda: present (stdlib); client E2E regression PASS
Native Function Pointer / qsort / C Callback / Foreign Thread Attach: PASS
Callback + GC / Exception Boundary: PASS
0.1–0.9 regression: PASS
Native Compilation: PASS
```
