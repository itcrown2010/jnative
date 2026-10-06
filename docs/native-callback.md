# Native Callback (0.10)

`Native.callback(sam)` exports a managed SAM as a C function pointer for FFI.

## API

```java
NativeCallbackHandle cb = Native.callback(x -> x * 2);
NativePtr fn = cb.pointer();  // trampoline address (i64 → ptr)
cb.close();                   // release slot
```

## Trampoline

1. C code calls the trampoline (C ABI).
2. Trampoline calls `jn_thread_attach_foreign()` for foreign-thread TLS.
3. Loads callable from `@jn.cb.callables[id]`.
4. Invokes via `jnative_invoke_*` runtime matching the SAM signature.
5. Uncaught managed exceptions return a safe default (e.g. `0` for `int`) — process does not abort.

## Lifetime

- `CreateNativeCallback` allocates a `NativeCallbackHandle` object retaining the SAM.
- `pointer()` returns the trampoline address stored in the handle field.
- `close()` clears the callback table slot and retained reference.
- **Non-capturing only** — capturing lambdas are rejected at compile time.

## Foreign threads

Callbacks may run on pthreads created outside JNative. The attach path ensures GC roots and exception TLS are valid for that thread during the call.

## Type inference

For `Native.callback(lambda)`:

| Params | Inferred SAM |
|--------|----------------|
| 0 | `Runnable` |
| 1 untyped | `IntUnaryFunction` |
| 2 with `NativePtr` | `IntCompare` |
| 2 int | `IntBinaryFunction` |

Pre-typed SAM variables (`IntCompare cmp = ...; Native.callback(cmp)`) are also supported.

## FFI argument compatibility

`NativeFunctionPtrType` from `pointer()` may be passed where `NativePtr` is expected (C ABI void*).

## Example: libc qsort

```java
IntCompare cmp = (a, b) -> Native.loadInt(a) - Native.loadInt(b);
NativeCallbackHandle cb = Native.callback(cmp);
LibC.qsort(base, n, elemSize, cb.pointer());
cb.close();
```

## Limitations

- 256 concurrent callback slots.
- No capturing callbacks.
- Exception boundary returns zero-like defaults, not C errno semantics.
