# Callable / Lambda Architecture (0.10)

作者：张晓达

JNative 0.10 adds first-class **SAM callables**: lambdas, method references, and closure capture without a JVM.

## Pipeline

```text
Lambda / MethodRef
  → SemanticAnalyzer (CallableBinding, env class)
  → IrGenerator (make_callable, invoke_callable)
  → LlvmIrGenerator (SAM object: metadata + code ptr + env ptr)
  → CallableRuntimeEmitter (jnative_invoke_* indirect dispatch)
```

## SAM builtins

| SAM | Method | Runtime |
|-----|--------|---------|
| `Runnable` | `run()` | `jnative_invoke_void` |
| `IntUnaryFunction` | `apply(int)` | `jnative_invoke_i32_i32` |
| `IntBinaryFunction` | `apply(int,int)` | `jnative_invoke_i32_i32_i32` |
| `Consumer` | `accept(Object)` | `jnative_invoke_void_ptr` |
| `IntCompare` | `compare(NativePtr, NativePtr)` | `jnative_invoke_i32_ptr_ptr` |
| `HttpHandler` | `handle(Object)` | `jnative_invoke_ptr_ptr` |

## Closure model

Non-capturing lambdas use a null env pointer. Capturing lambdas allocate a synthetic env class (`__jn.Env.N`) with one field per captured variable. The env is GC-managed; the SAM object holds a reference to it.

Capturing variables must be **effectively final**. Assignment after capture is rejected at compile time.

## Method references

- **Static:** `Type::method` → direct thunk to static method.
- **Bound:** `receiver::method` → thunk stores receiver in env or binding.
- **Virtual:** dispatch uses vtable when `isVirtualMethodRef` is set.

## Thread integration

`Thread(Runnable)` accepts SAM callables. Closures captured on one thread may be invoked on another; the env object stays alive via the SAM reference until GC.

## IR instructions

- `make_callable` — allocate SAM object, store code pointer and env.
- `invoke_callable` — load code/env, indirect call through `jnative_invoke_*`.

## Limitations

- No generic SAM inference beyond builtin SAMs.
- No serializable / MethodHandle API.
- HttpHandler uses `Object → Object`; stdlib casts to `HttpResponse`.
- Constructor / array constructor method references: not supported (hard error).
- wasm32-browser: callables rejected (see `docs/jnative-closures.md`).

## Closure reliability (2026-10-09)

Nested lambda frames, `this` capture, escape+GC, unbound instance refs, and invoke-runtime coverage are documented in **[jnative-closures.md](jnative-closures.md)**.
