# JNative Object Model

## Reference semantics

Class-typed variables store references. Assignment copies the pointer.

## Object header (0.4)

```text
┌────────────────────┐
│ ClassMetadata*     │  ← 8 bytes
├────────────────────┤
│ instance fields…   │
└────────────────────┘
```

`ClassMetadata` holds `vtable*`, `super*`, `instanceSize`, flags, name.

## Allocation

```text
NewObject → jnative_alloc_zeroed → store @meta.Class → ConstructorCall → ref
```

## Field layout

`ObjectLayoutBuilder` / `ClassLayout` is the source of truth. Reference field GEP indices are recorded on `ClassMetadata` for future GC.

## Dispatch

Load metadata from object → load vtable from metadata → index slot → indirect call.

## Null

Field access / instance calls use `jnative_null_check`.

## Future (0.5+)

- GC scanning using reference-field metadata and array flags
- Interfaces / itable on ClassMetadata
- Exceptions replacing abort-style runtime errors
