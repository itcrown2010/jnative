# Target ABI (0.8)

作者：张晓达

## Purpose

Separate Java/JNative types from C calling conventions and fixed-width integer sizes.

## Targets

| Class | Role |
|-------|------|
| `X86_64SystemVAbi` | Linux / macOS x86_64 |
| `X86_64WindowsAbi` | Windows x86_64 (stub) |
| `AArch64Abi` | Apple Silicon / aarch64 |

Host selection: `TargetAbi.forHost()`.

`sizeOf` / `alignOf` for `AbiType` live on `TargetAbi` — never hardcode pointer width in layout code.

## Calling convention

Default: `CallingConvention.C`.

Future: stdcall / fastcall / vectorcall — modeled but not implemented.

## Important rule

```text
Java long (64-bit) ≠ C long (platform-dependent)
```

FFI uses `AbiType.I64` / `AbiType.SIZE`, never soft “C long”.

## Structs

`NativeStructLayout` + `NativeStructLayoutBuilder` are the **sole** native field-layout source.
Surface: `@NativeStruct`. See [native-struct.md](native-struct.md).

Struct-by-value ABI is **not** supported in 0.8 — pass `NativePtr<T>` (`T*`).
