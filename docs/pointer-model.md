# Pointer Model (0.8)

## NativePtr&lt;T&gt;

Compiler-recognized typed native pointer (not full Java generics).

| Form | Meaning |
|------|---------|
| `NativePtr` / `NativePtr<Void>` | `void*` |
| `NativePtr<Point>` | `Point*` |
| `NativePtr.NULL` | native null (≠ Java `null`) |

- Not a GC-managed reference; GC never scans the target.
- Size = `TargetAbi.pointerSize()` (never hardcode 8 in user code / layout).
- `NativePtr<Point>` ↛ `NativePtr<Other>` implicitly.
- `NativePtr<T>` → `NativePtr<Void>` allowed; reverse requires an explicit cast.

## Provenance

IR/semantic tracking:

| Provenance | Source |
|------------|--------|
| `STACK` | `Native.addressOf(@Stack …)` |
| `NATIVE_HEAP` | `Native.allocate<T>()` / `NativeMemory.allocate` |
| `NULL` | `NativePtr.NULL` |
| `EXTERNAL` | (reserved) |
| `UNKNOWN` | default |

Escape analysis rejects `STACK` pointers that return or store into managed fields. Native calls are treated as non-capturing in 0.8.

## Allocation worlds

| World | API | Ownership |
|-------|-----|-----------|
| Managed Heap | `new Person()` | GC |
| Stack | `@Stack Point p = new Point()` | Automatic (frame) |
| Native Heap | `NativeMemory.allocate` / `Native.allocate<T>()` | Manual `free` |

Do not mix lifetimes across worlds without an explicit ownership story.
