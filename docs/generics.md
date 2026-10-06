# JNative Generics (0.15)

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

## Limits (0.15)

- Implicit raw types rejected
- Generic array creation `new T[n]` unsupported
- Generic `@NativeStruct` unsupported
- Parameterized `instanceof` / checked casts are erased (unchecked)
- Complex interface primitive specialization reuses erased ITable + concrete impl ABI
- Full javac inference / capture conversion is minimal
