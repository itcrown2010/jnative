# JNative Memory Model (0.8 / 0.9)

Concurrent access rules (volatile / Mutex / Atomic / start-join) are in
[concurrency-memory-model.md](concurrency-memory-model.md).

## References

Class / array / String values are managed pointers to object payloads.

## Heap object

```text
GcHeader (runtime-private)
  next allocation*
  size
  mark
  flags

Object (language-visible)
  ClassMetadata*
  instance fields / array header+elements / string bytes
```

## ClassMetadata (RTTI + GC)

```text
vtable*, super*, instanceSize, flags, name*,
refFieldCount, refFieldOffsets*
```

`refFieldOffsets` lists byte offsets of reference fields (inherited fields included).

Array metadata uses flags:

- `FLAG_ARRAY`
- `FLAG_REF_ELEMENTS` when elements are references

## Allocation ownership

| Kind | Allocator | Ownership |
|------|-----------|-----------|
| `new Person()` (managed) | GC heap | GC |
| `@Stack Point p = new Point()` | native stack (`alloca`) | Automatic (frame) |
| `Native.allocate<Point>()` / `NativeMemory.allocate` | native heap (`malloc`) | Manual `free` |
| `new T[n]` / String | GC heap | GC |
| `File.read*` temps | native heap (`malloc`) | Always freed before return |
| `File.read*` results | GC heap (`String` / `byte[]`) | GC |

Managed and native worlds must not be mixed without an explicit bridge (FFI / CString / File copy).

## GC movement (default GC2)

Default collector is **GC2** — see [gc-current-design.md](gc-current-design.md).

| Heap region | Pointer stability |
|---|---|
| Young | **May relocate** on minor/major GC |
| Old / large | Stable until freed (non-moving mark-sweep) |
| `@Stack` / native malloc | Not GC-moved |

Language references are updated via precise root slots. Do **not** pass raw managed object addresses to FFI across safepoints — use `CString` / `NativeMemory` / handle indirection.

Legacy `JNATIVE_GC=marksweep` is fully non-moving but is **not** the default.
