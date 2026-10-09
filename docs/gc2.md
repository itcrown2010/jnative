# JNative GC2 (0.13)

作者：张晓达

Generational garbage collector with TLAB bump allocation and young semi-space copying.

## Mode selection

| `JNATIVE_GC` | Behavior |
|--------------|----------|
| `generational` (default) | GC2 — TLAB + nursery + minor/major |
| `marksweep` | Legacy 0.5/0.9 non-moving collector (LLVM IR) |

## Heap

```text
Young (semi-space From/To)  — bump / TLAB
Old                         — malloc list + mark-sweep
Large (≥ JNATIVE_LARGE_THRESHOLD) — malloc list
```

## Configuration

| Variable | Default | Meaning |
|----------|---------|---------|
| `JNATIVE_YOUNG_SIZE` | 4MiB | Size of each semi-space |
| `JNATIVE_TLAB_SIZE` | 32KiB | TLAB chunk |
| `JNATIVE_PROMOTION_AGE` | 2 | Survivals before old |
| `JNATIVE_LARGE_THRESHOLD` | 32KiB | Direct large/old |
| `JNATIVE_GC_STRESS` | | `minor` / `major` / `all` / `1` (soft) |
| `JNATIVE_GC_VERIFY` | | Stale abandoned-space check |
| `JNATIVE_GC_LOG` | | Minor/major pause logs |
| `JNATIVE_GC_STATS` | | atexit counters |

## Mutator contract

- Small `new` → TLAB bump (no global lock on fast path)
- Managed `StoreField` / ref `StoreElement` → `jn_gc2_write_barrier`
- Roots are **updateable slots** (shadow stack, statics, ex/Thread slots)
- Stack-managed objects (0.12) stay non-moving; their ref fields remain roots

See also [generational-gc.md](generational-gc.md), [tlab.md](tlab.md), [write-barrier.md](write-barrier.md), [gc-root-audit.md](gc-root-audit.md).
