# JNative Memory Model (0.5)

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

| Kind | Allocator |
|------|-----------|
| `new T` | GC payload + store metadata |
| `new T[n]` | GC payload (meta, length, elements) |
| String literal/concat | GC payload with inline UTF-8 |

No unmanaged `malloc` for Java-visible objects outside the GC allocator.

## Non-moving

Pointers remain stable for object lifetime. No read/write barriers in 0.5.
