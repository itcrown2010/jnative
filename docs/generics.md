# JNative Generics (0.15)

作者：张晓达

## Strategy

| Kind | Lowering |
|------|----------|
| Reference type arguments (`Box<String>`, `Box<Person>`) | **Erasure** — shared `Box` layout / methods (`managed_ref`) |
| Primitive type arguments (`Box<int>`, `Pair<int,long>`) | **Specialization** — `Box$P_i32`, concrete primitive fields |

Primitive type arguments are a **JNative extension** (not Java SE). They avoid boxing.

## Pipeline

```text
AST
 → Generic semantic types (TypeVariable, ParameterizedType, WildcardType)
 → Type inference / substitution
 → GenericLowering (erasure | specialization)
 → Concrete IR
 → LLVM (no source-level generics)
```

## CLI

- `--emit-generics` / `--explain-generics` — print lowering report

## Naming

Specialized classes: `Origin$P_<argcodes…>` e.g. `Box$P_i32`, `Pair$P_i32_i64`.

## Limits

- Implicit raw types rejected (hard error)
- Inside generic bodies, only `T`/`null` assign to fields/params of type `T`
- `new T[n]` in erased bodies lowers as reference arrays (`Object[]`-shaped); prefer specialization for primitives
- Generic `@NativeStruct` unsupported
- Parameterized `instanceof` / checked casts are erased (unchecked)
- Complex interface primitive specialization reuses erased ITable + concrete impl ABI
- Full javac inference / capture conversion / wildcards is minimal
- No `Integer` auto-boxing — use `ArrayList<int>` specialization or reference elements
- Iterable enhanced-for supported via stdlib (`jnative-generics-iterable.md`)
