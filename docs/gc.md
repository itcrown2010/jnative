# JNative GC

**0.13 default:** generational GC2 — see [gc2.md](gc2.md).

Legacy mark-sweep (`JNATIVE_GC=marksweep`) remains available:

## Algorithm (legacy marksweep)

```text
Precise · Stop-the-world · Mark-Sweep · Non-moving · Multi-thread aware
```

See `docs/safepoints.md`.

## Object layout

```text
[ GcHeader 24B | Object payload ]
GcHeader: next*, size, mark, flags
Java reference → payload (ClassMetadata* at offset 0)
```

GC maps `object ↔ header` with a fixed ±24 byte offset.

## Allocation

All managed heap traffic goes through:

```text
jnative_gc_alloc / jnative_alloc_zeroed
jnative_array_alloc
jnative_string_*
```

Every allocation is linked on `@jn.gc.heap_head` (intrusive list) so the runtime can **enumerate** all managed objects.

## Roots (precise per-thread shadow stacks)

```text
GcFrame { prev*, rootCount, slotAddr[n] }   // TLS head per ThreadState
```

- Enter method: `jnative_gc_frame_push` + register every `ptr` alloca address
- Exit / return: `jnative_gc_frame_pop`
- GC scans **all** registered threads via `jn_foreach_root_slot` + TLS exceptions + Thread objects + static managed fields
- Global root (0.6): `@jn.ex.current` scanned in `jnative_gc_mark_from_roots`
- Reference locals, parameters, `this`, and protected reference temporaries are PTR allocas
- **0.12:** stack-allocated managed objects also register each **reference field slot** as a root (object itself is not on the heap; `jnative_gc_header_in_heap` rejects stack pointers during mark)

Roots store **addresses of slots**, not copied pointer values, so updates remain visible.

Compiler rule: any runtime call that may allocate is a **safepoint**. All still-live managed references must already be in the precise root system.

## Mark

Worklist (not native recursion):

1. Clear marks on the heap list
2. Walk shadow-stack frames → mark roots
3. Pop worklist → scan object using ClassMetadata

## Object scanning

`ClassMetadata.refFieldOffsets` (byte offsets, flattened including inherited fields).

Arrays: `FLAG_ARRAY` + `FLAG_REF_ELEMENTS` → scan `length` elements at +16; primitive arrays are not scanned as pointers.

## String

UTF-8 bytes are **inline** in the String object allocation (`meta*`, `length`, `bytes…`) so there is no orphan backing buffer when the String is freed.

## Sweep

Unmarked headers are unlinked and `free`d (poison `0xDD` under stress). Survivors clear mark bits. Stats update `live_bytes` / `live_objects`.

## Trigger

```text
allocated_bytes >= threshold  →  collect
```

Default threshold 1 MiB. After GC: `threshold = max(1MiB, live_bytes * 2)`.

Env:

| Variable | Effect |
|----------|--------|
| `JNATIVE_GC_THRESHOLD` | initial threshold (bytes) |
| `JNATIVE_GC_STRESS=1` | collect on every allocation |
| `JNATIVE_GC_STATS=1` | print stats at process exit |

## System.gc()

Implementation-specific intrinsic → `jnative_gc_collect()`.

## Known limitations

- No generational / concurrent / moving GC
- No threads / safepoint handshake
- No interfaces / exceptions yet
- Shadow stack is correctness-oriented, not optimized
