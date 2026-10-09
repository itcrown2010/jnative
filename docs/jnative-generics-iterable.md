# Generics type safety and Iterable enhanced-for

**Date:** 2026-10-09  
**Scope:** Language core (no ERP / mobile).

## Strategy (unchanged architecture)

| Kind | Lowering |
|---|---|
| Reference type arguments (`Box<String>`) | **Erasure** — shared class layout (`managed_ref`) |
| Primitive type arguments (`Box<int>`) | **Specialization** — `Box$P_i32` (JNative extension, not boxing) |

Pipeline: semantic `JType` → substitution/inference → `GenericLowering` → IR → LLVM.

Do **not** maintain a second parallel generics story; this mixed erase/specialize path is the only runtime model.

## What this round hardened

1. **Type variable assignability** — inside a generic body, only `T` / `null` assign to `T` (blocks `this.value = "x"` when field is `T`).
2. **Constructor matching** — parameter types are substituted with class type arguments before overload resolution (`new Box<String>(...)`).
3. **Upper bound member lookup** — `T extends Base` / interface bounds resolve methods on the bound.
4. **Iterable enhanced-for** — `iterator()` once, then `hasNext`/`next` per iteration (stdlib `ArrayList`).

## Supported

- Class/interface type parameters, arity checks, invariance (`Box<Child>` ↛ `Box<Parent>`).
- Single upper bound `T extends Base`.
- Basic generic methods + inference (`static <T> T identity(T v)`).
- Explicit type arguments on `new` / methods (existing).
- Raw types: **hard error** (except `instanceof` erasure context).
- Array foreach (prior) + **Iterable foreach** (this round).

## Not supported (explicit)

- Wildcards as a full subtyping story (`List<?>` assignability).
- Multiple bounds `T extends A & B`.
- Capture conversion / javac-complete inference.
- ~~Auto-boxing~~ — now available for `Integer`/`Long`/`Boolean` (see `docs/jnative-boxing-collections.md`). `ArrayList<int>` specialization remains.
- Concurrent-safe iteration — `ArrayListIterator` fail-fast via `modCount` only; no concurrency claims.
- WASM: not re-validated this round; treat as unknown until a dedicated run.

## Iterable foreach contract

```java
for (Item item : items) {
    total = total + item.quantity;
}
```

- Collection expression evaluated as receiver of a single `iterator()` call.
- Each iteration: `hasNext()` then `next()`.
- `break` / `continue` / nesting OK; safepoint on loop back-edge.
- Exhausted `next()` throws `NoSuchElementException` (stdlib iterator).
- Structural modification during iteration throws `ConcurrentModificationException` when `modCount` mismatches.
- Non-array, non-Iterable → compile error.

## Files

| File | Purpose |
|---|---|
| `TypeVariable.java` | Strict assignability into `T` |
| `SemanticAnalyzer.java` | Bounds lookup, Iterable extraction, foreach desugar, ctor substitution |
| `EnhancedForDesugar.java` | Stored iterator/hasNext/next calls |
| `TypedCompilationUnit.java` | Carries desugar map |
| `IrGenerator.java` | Iterable foreach IR |
| `GenericLowering.java` | Pass-through desugar map |
| `GenericsIterableEndToEndTest.java` | Positive/negative + native e2e |
| `examples/GenericIterableDemo.java` | Demo |
| `docs/generics.md` | Strategy (updated limits) |

## Tests run

`SwitchEnumEndToEndTest`, `FinalDaForeachEndToEndTest`, `GenericsEndToEndTest`, `GenericsIterableEndToEndTest`, `PrimitiveOpsEndToEndTest`, `CollectionsEndToEndTest`.
