# JNative Architecture

```text
.java
  → Lexer / Parser / AST
  → Semantic (types, constructors, arrays, String, RTTI, exceptions)
  → JNative IR (Throw, try CFG, GcCollect, PTR root allocas)
  → LLVM (ClassMetadata, shadow stack, GC + exception runtime)
  → clang → native executable
```

## Key packages

- `semantic` — `ClassMetadata` with flattened reference byte offsets; Throwable hierarchy
- `layout` — object payload layout (ClassMetadata* header)
- `backend.llvm.runtime` — Memory / Gc / **Exception** / Array / String / Type / Io emitters

## GC (0.5)

Precise STW mark-sweep; Java refs point past a 24-byte `GcHeader`; roots via shadow stack of PTR alloca addresses. Exception current pointer is also a precise GC root.

## Exceptions (0.6)

Explicit pending-exception propagation. See [exceptions.md](exceptions.md).
LLVM does not perform Java catch-resolution; IR already encodes dispatch CFG.

## FFI (0.7)

`@Native` / `native` methods lower to `NativeCall` + Target ABI. See [ffi.md](ffi.md), [abi.md](abi.md), [native-memory.md](native-memory.md).

## Native Data Model (0.8)

`@NativeStruct` / `@Stack` / typed `NativePtr<T>` / `StackAlloc` / `AddressOf` / escape analysis.
See [native-struct.md](native-struct.md), [stack-allocation.md](stack-allocation.md), [pointer-model.md](pointer-model.md).

## File I/O (0.8.1)

Builtin `File` → `FileRuntimeEmitter` (libc today). See [file-io.md](file-io.md).
`main(String[] args)` receives a real managed argv array.

## Networking (0.8.2)

Builtin `TcpSocket` / `TcpServer` → `NetworkRuntimeEmitter` → PlatformSocket C layer
(`jnative_plat_tcp_*`, POSIX today; Winsock stub reserved). See [networking.md](networking.md).
No public fd/`SOCKET`; DNS via `getaddrinfo`; blocking I/O only.

## HTTP (0.8.3)

HTTP/1.1 client + server as **JNative stdlib** (`stdlib/http/HttpLib.java`) above `TcpSocket`.
**No** HTTP compiler intrinsics. See [http.md](http.md).

## Multithreading (0.9)

Real OS threads via `jnative_mt.c` (POSIX). Per-thread `ThreadState` (TLS): shadow-stack roots,
exception state, MANAGED/NATIVE/SAFEPOINT. Multi-thread STW mark-sweep with safepoint polls
(alloc / method entry / loop backedge). See [threading.md](threading.md), [safepoints.md](safepoints.md),
[runtime-locking.md](runtime-locking.md).

## Invariants

1. Managed field layout: `ClassLayout`. Native field layout: `NativeStructLayout` (via `TargetAbi`)
2. LLVM does not re-resolve Java methods, types, catch matching, `@Native`, or C struct semantics
3. All managed allocations are GC-tracked and enumerable
4. Roots are precise (not conservative stack scans)
5. Exception objects are ordinary GC-managed instances
6. `NativePtr` / `@NativeStruct` are not managed references; GC ignores their targets
7. `@Stack` NativeStruct lowers to `alloca` — never a GC allocation
8. File API does not expose `FILE*`; platform I/O stays in the runtime layer
9. Network API does not expose socket handles; PlatformSocket isolates OS networking
10. HTTP is library code on TcpSocket — not LLVM/compiler special cases
11. Each managed OS thread has its own ThreadState; GC scans all thread roots under STW
12. Blocking native calls enter NATIVE so STW does not wait on them
