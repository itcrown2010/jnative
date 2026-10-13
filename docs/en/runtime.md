# Runtime

Compiled JNative programs link against a small **native runtime** (C and platform code under `src/main/resources/native/`). That runtime is not a JVM: there is no bytecode interpreter, classloader, or HotSpot.

---

## Memory and GC

Default mode is **GC2** (generational):

- Young generation: semi-space copying with TLAB bump allocation
- Old generation: malloc-backed mark-sweep
- Large objects may bypass the nursery

Select via environment:

| `JNATIVE_GC` | Meaning |
|--------------|---------|
| `generational` (default) | GC2 |
| `marksweep` | Older non-moving collector path |

Roots are **precise**: shadow-stack slots and other updatable root cells so a moving collector can relocate objects. Managed Java-like references point past a GC header; unmanaged native pointers are ignored by the tracer.

More detail: [`../gc2.md`](../gc2.md), [`../tlab.md`](../tlab.md).

---

## Exceptions

The compiler encodes catch dispatch in IR. At runtime, a pending-exception flag / value propagates; LLVM does not invent Java exception tables on its own. Try-with-resources is lowered in the frontend/IR according to the declaration form supported by the language profile.

See [`../exceptions.md`](../exceptions.md).

---

## Threads and safepoints

On POSIX hosts, `Thread` and locking primitives map to OS threads (`jnative_mt.c`). Each managed thread keeps a `ThreadState` (roots, exception state, MANAGED / NATIVE / SAFEPOINT). GC is stop-the-world and coordinates via safepoint polls (allocation, method entry, loop backedges). Blocking native calls enter **NATIVE** so STW does not sit on a syscall forever.

See [`../threading.md`](../threading.md), [`../safepoints.md`](../safepoints.md).

---

## Native interop

| Feature | Role |
|---------|------|
| `@Native` / `native` methods | Lower to direct C calls (`NativeCall`) with a known ABI |
| `@NativeStruct` / `NativePtr` | Unmanaged memory; not traced as managed refs |
| `@Stack` native structs | `alloca`-style; never a GC allocation |
| Effect annotations | Constrain GC/throwing/blocking behavior for optimization and safety |

See [`../ffi.md`](../ffi.md), [`../native-struct.md`](../native-struct.md), [`../effects.md`](../effects.md).

---

## I/O stack

```text
Language / stdlib API
        ↓
LLVM runtime emitters (File, Network, …)
        ↓
Platform C (posix today; windows stubs where incomplete)
```

- **Files** — no `FILE*` in the public API  
- **TCP** — no raw socket handles; blocking I/O  
- **HTTP** — pure stdlib (`stdlib/http`) on TCP  

Treat Linux async poll and Windows networking as **incomplete** until the release-blocker list says otherwise.

---

## Configuration knobs (GC)

Common environment variables (see `gc2.md` for defaults): `JNATIVE_YOUNG_SIZE`, `JNATIVE_TLAB_SIZE`, `JNATIVE_GC_STRESS`, `JNATIVE_GC_LOG`, `JNATIVE_GC_STATS`.
