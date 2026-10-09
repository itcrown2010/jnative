# JNative FFI (0.7 / 0.8 / 0.9)

作者：张晓达

Blocking / long native calls should transition MANAGED→NATIVE (0.9). Short libc calls remain
fine on multiple threads for JNative TLS state; third-party library thread-safety is separate.

## Architecture

```text
@Native / native method
  → NativeFunction + NativeSignature + AbiType
  → IrInstruction.NativeCall
  → LLVM declare/call
  → NativeLinker (-l…)
  → libc / external libraries
```

Not JNI. JNative has no JVM.

LLVM backend does **not** parse `@Native` attributes. Frontend/semantic builds `NativeFunction`.

## Declarations

```java
class LibC {
    @Native(symbol = "puts")
    static native int puts(String text);
}
```

- native methods have **no body**
- optional `symbol`, `library`
- default symbol = Java method name

## ABI

Java types map to fixed-width ABI types (`AbiType`), not platform `C long`.

| Surface | ABI |
|---------|-----|
| int | i32 |
| long | i64 |
| boolean | i32 |
| char | u16 |
| String | temporary CString (`char*`) |
| NativePtr | pointer |

`TargetAbi` abstracts System V / Windows / AArch64 (0.7 uses the same fixed-width primitive map).

## NativePtr

Compiler-level machine pointer (`IrType.NATIVE_PTR`).

- **not** a GC-managed object
- GC never treats it as `ClassMetadata*`
- distinct from Java `null` — use `NativePtr.NULL`

Managed references cannot convert to/from `NativePtr` implicitly.

## CString

`jnative_ffi_string_to_cstr` copies UTF-8 + NUL into a **malloc** buffer valid for the duration of the native call, then `jnative_ffi_cstr_free`.

Do not rely on GC non-moving for FFI pointers into Java objects.

`NativeMemory.fromCString(ptr)` copies a native `char*` into a GC `String`.

## NativeMemory

```java
NativePtr p = NativeMemory.allocate(16L);
NativeMemory.putInt(p, 0L, 123);
int x = NativeMemory.getInt(p, 0L);
NativeMemory.free(p);
```

Uses `malloc`/`free`. **Not** GC-managed. Unsafe: bad offsets may crash.

## Exceptions / GC

- Entering a native call requires no pending JNative exception
- Native returns do not set JNative exceptions (0.7)
- Native call transitions thread to NATIVE; GC may still update registered root **slots**
- Do **not** pass movable managed object addresses to C across safepoints — use CString / NativeMemory copies
- Callbacks hold managed callables via updateable GC root handles (0.10 / 0.13 moving GC)

## Linking

Libraries from `@Native(library = "…")` are passed as `-l…` to clang.
CLI: `--library-path` / `--link`.

## Struct pointers (0.8)

```java
@Native(symbol = "point_sum")
static native double pointSum(NativePtr<Point> p);
```

ABI: `Point*` — no JNI handles, no GC pinning. Prefer pointer params; struct-by-value is not supported.

Stack pointers may be passed for the duration of the call (non-capturing contract).

## Limitations

No JNI, callbacks, function pointers as values, C++ ABI, struct-by-value, union/bitfields, header parser, full annotations.
