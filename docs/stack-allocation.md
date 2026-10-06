# Stack allocation (managed objects)

`StackAllocationPass` (enabled at `-O2`+) rewrites remaining `NO_ESCAPE` `NewObject` instructions to **stack-managed** storage.

## Distinct from `@Stack NativeStruct`

| | `@Stack` NativeStruct | Optimizer stack-managed object |
|--|----------------------|--------------------------------|
| Layout | native C layout | managed class layout + `ClassMetadata*` |
| GC | not a GC object | not on GC heap; **reference fields are precise roots** |
| Escape | compile-time `@Stack` | requires escape analysis (`NO_ESCAPE`) |

## Semantics retained

- Zero-init, metadata install, constructor / field init still run
- Field load/store and `==` identity behave as for heap objects of the same type
- Lifetime = current activation; must not escape

## GC roots

For each stack-managed object, LLVM registers every **managed reference field slot** on the shadow stack (`jnative_gc_frame_set_root`). The object pointer itself is not a heap object; `jnative_gc_mark_push` rejects non-heap addresses via `jnative_gc_header_in_heap`.

Example:

```java
Holder h = new Holder();   // stack at -O2
h.person = make();         // Person heap (RETURN_ESCAPE)
System.gc();               // must see h.person
```

## Interaction with scalar replacement

Primitive-only non-escaping objects (e.g. `Point`) are preferably **scalar-replaced** first; stack allocation covers cases with reference fields that remain after that pass.
