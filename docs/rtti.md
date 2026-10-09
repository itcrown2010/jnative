# Runtime Type Information (RTTI)

作者：张晓达

## ClassMetadata

Objects store `ClassMetadata*`. Metadata includes:

```text
vtable*
super ClassMetadata*
instanceSize
flags (array / ref-elements)
name*
```

Compiler-side extras for GC readiness:

- reference field GEP indices
- array element type kind

Reserved for later: interface lists / itable (do not assume only a superclass chain forever).

## `instanceof`

```text
jnative_instanceof(object, targetMetadata)
```

Walks the superclass chain. `null instanceof T` is `false` (no NPE).

IR: explicit `InstanceOf` opcode.

## Checked cast

```text
jnative_checked_cast(object, targetMetadata)
```

- `null` casts succeed (`null` result).
- Invalid cast → `JNative runtime error: invalid cast` (nonzero exit).
- Later → `ClassCastException`.

IR: explicit `CheckedCast` opcode.

Semantic analysis may reject compile-time impossible casts; related types still
emit a runtime check.
