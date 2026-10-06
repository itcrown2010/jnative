# Native Memory (0.7)

## Boundary

```text
JNative Heap  → GC managed
Native Heap   → manual ownership (malloc/free)
```

Never free a Java object with `NativeMemory.free`.
Never expect GC to reclaim a `NativePtr` target.

## API

| Method | Role |
|--------|------|
| `NativeMemory.allocate(long)` | `malloc` |
| `NativeMemory.free(NativePtr)` | `free` |
| `getInt` / `putInt` | unaligned load/store at byte offset |
| `fromCString` | copy `char*` → GC String |

## Risks

Native memory operations are **unsafe** by default:

- use-after-free
- double-free
- out-of-bounds writes
- process crash / memory corruption

0.7 does not provide production bounds metadata.

## GC

`NativePtr` values are not managed references. Shadow-stack roots only track Java `PTR` allocas.
