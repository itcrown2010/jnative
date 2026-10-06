# Write barrier (GC2)

After every managed reference store (`StoreField`, reference `StoreElement`), LLVM emits:

```text
call void @jn_gc2_write_barrier(owner, value)
```

Runtime:

```text
if owner is old/large AND value is young:
    remember owner (dirty bit + remset)
```

Minor GC scans roots, remset, then conservatively rescans the old/large lists for any remaining old→young edges (0.13 correctness bias).

Stack-managed receivers do not need card barriers; their reference fields are root slots.
