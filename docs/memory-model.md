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

## Non-moving

Managed pointers remain stable for object lifetime (GC is non-moving). Do **not** rely on this for FFI into Java objects — use temporary CString / copies.
