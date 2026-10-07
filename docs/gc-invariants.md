# JNative GC Invariants

## Managed pointers

- Every managed object payload begins with a `ClassMetadata*` at offset 0.
- Java/IR reference values point at the **payload**, not the GC header.
- GC2 header is 32 bytes immediately before the payload (`JN_GC_HEADER_SIZE`).
- Reference fields are listed in `ClassMetadata.ref_offsets` (byte offsets from payload start). Arrays of refs use metadata flags + element base at offset 16.

## Roots

Collectors treat these as precise roots:

| Root set | Mechanism |
|---|---|
| Shadow stack | Per-thread GC frames (`jnative_gc_frame_push` / TLS `jn_tls_frame`) holding slots for live locals/temps |
| Pending exception | TLS `ex_current` visited via `jn_foreach_ex_root` / `_slot` |
| Thread objects | Registered managed `Thread` instances |
| Statics | Module static-root visitor (`jn_gc2_set_static_root_visitor`) |

`protectRef` in IR lowering keeps values that may be stored across a GC-safe call in a shadow-stack slot.

## When GC can run

- Explicit: `System.gc()` → `jnative_gc_collect` / `jn_gc2_collect`.
- Allocation pressure: nursery / threshold / TLAB refill failure.
- Stress: `JNATIVE_GC_STRESS=1|minor|major|all` (also CLI `--gc-stress[=…]` for `jnative run`, which sets the env before exec).
- Mutators poll safepoints around alloc / native transitions; STW parks other threads.

GC must **not** run inside `@NoGC` regions (`jn_nogc_depth > 0` aborts on managed alloc).

## STW sequence (cooperative safepoints)

```
Thread A        Thread B        GC
   │               │            │
allocate         mutate          │
   │               │          request
   ├───────────────┼────────────→
 safepoint       safepoint
   │               │
   └──── STOP ─────┘
                    ↓
                scan roots
                    ↓
                 collect
                    ↓
              resume all
```

| Phase | Runtime |
|---|---|
| request | Collector sets `gc_requested` in `jn_gc_stw_begin` (after `jn_gc_collect_lock`) |
| safepoint | Mutators hit `jn_safepoint_poll` (alloc / leave-native); park as `JN_EXEC_SAFEPOINT` |
| STOP | STW waits until no other thread remains `JN_EXEC_MANAGED` (`NATIVE`/`SAFEPOINT`/`TERMINATED` ok) |
| scan roots | `jn_foreach_root_slot` + exception / Thread / static visitors |
| collect | Young/old/mark-sweep work under world-stopped |
| resume all | `jn_gc_stw_end` clears request, bumps `stw_generation`, wakes parked polls |

## Write barriers / moving GC

Under GC2 (generational), stores of managed refs into object fields go through `jn_gc2_write_barrier` so old→young edges enter the remembered set. After a minor collection, root slots and remset owners are updated to forwarded addresses.

## Heap verify

`jnative_gc_verify_heap` / `jn_gc2_verify_heap` walks young/old/large (or mark-sweep list) and checks each live object's `ClassMetadata*` is non-null. Intended for tests and stress runs, not hot paths.

## Modes

| `JNATIVE_GC` | Behavior |
|---|---|
| unset / `generational` | GC2: TLAB + young semi-space + old mark-sweep |
| `marksweep` | Legacy non-moving mark-sweep in LLVM runtime |
