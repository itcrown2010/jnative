# TLAB

Each managed `JNThreadState` holds:

```text
tlab_top, tlab_end, tlab_start
```

Allocation fast path (in `jn_gc2_alloc`):

```text
if top + size <= end:
    object = top; top += alignedSize; return object
else:
    refill from nursery or trigger minor GC
```

Unused TLAB tails are filled with `FLAG_FILLER` headers before minor GC so the eden region stays a contiguous walkable object list.

Foreign attach/detach clears TLAB; STW retires all TLABs via `jn_tlab_retire_all`.
