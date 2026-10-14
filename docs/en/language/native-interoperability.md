# Native interoperability

**Status:** Experimental · **Example:** `examples/NativeCallback.java` · **Tests:** `FfiEndToEndTest`

## What it is

FFI to call native code and (where supported) receive callbacks—**not** a mobile app framework.

## Why it exists

System libraries and performance-critical C code need a controlled boundary outside the managed heap.

## Common mistakes

- Equating FFI with “Android/iOS app support” — see [mobile/status.md](../mobile/status.md).  
- Using FFI on `wasm32-browser` — **Not Supported**.

## Related

[sdk/ffi.md](../sdk/ffi.md) · [architecture/ffi.md](../architecture/ffi.md)
