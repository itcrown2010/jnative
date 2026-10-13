# Runtime

The **native runtime** is linked into programs produced by the compiler. It is not a JVM.

---

## Garbage collection

- Default path: **GC2** generational collector (TLAB allocation, young semi-space copy, old mark-sweep).  
- Precise rooting via shadow stacks and updatable root slots (compatible with moving GC).  
- Multi-threaded STW with safepoint polls (allocation, method entry, loop backedges).  

Docs: [`../gc2.md`](../gc2.md), [`../gc.md`](../gc.md), [`../safepoints.md`](../safepoints.md).

---

## Exceptions

Pending-exception style propagation; catch dispatch is encoded in IR CFG before LLVM.  
WASM uses the same model where exceptions are supported for that target.

Docs: [`../exceptions.md`](../exceptions.md).

---

## Threading

POSIX-backed multithreading (`jnative_mt.c` and related). Per-thread state holds roots, exception state, and MANAGED/NATIVE/SAFEPOINT modes.

Docs: [`../threading.md`](../threading.md), [`../runtime-locking.md`](../runtime-locking.md).

---

## Native FFI

- `@Native` methods and native structs  
- Managed references vs unmanaged `NativePtr` / `@NativeStruct` / `@Stack`  
- Effect annotations (`@NoGC`, `@NoThrow`, …) interact with safepoints and contracts  

Docs: [`../ffi.md`](../ffi.md), [`../native-struct.md`](../native-struct.md), [`../effects.md`](../effects.md).

---

## I/O and networking

| Layer | Notes |
|-------|--------|
| File | Builtin `File` → runtime emitter (libc-oriented today) |
| TCP | `TcpSocket` / `TcpServer` → platform socket layer |
| HTTP | Stdlib on TCP — not compiler intrinsics |

Platform stubs (e.g. incomplete Windows/Linux poll backends) are tracked as release blockers — do not assume production parity everywhere.

Docs: [`../file-io.md`](../file-io.md), [`../networking.md`](../networking.md), [`../http.md`](../http.md).
