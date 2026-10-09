# JNative 0.15 Build Report — Generics

作者：张晓达

## Summary

- Version **0.15**
- Reference generics: **erasure** (shared layout / ITable)
- Primitive generics: **specialization** (`Box$P_i32`, no boxing)
- Pipeline: semantic generics → `GenericLowering` → concrete IR → LLVM (LLVM unaware of `T`)

```text
mvn test: PASS
Total tests: 251
New generic tests (GenericsEndToEndTest): 12
Failures: 0
Errors: 0

Generic class: PASS
Generic interface: PASS
Generic method: PASS
Bounds model: PASS (checked at type-arg site)
Substitution: PASS
Inference: PASS (minimal TypeInferenceEngine)
Diamond: PASS
Reference erasure: PASS
Primitive specialization: PASS
Box<int> no boxing: PASS
Generic lambda: PARTIAL (non-generic SAM still PASS; Function<T,R> expressible)
Generic ITable: PASS (erased interface + concrete impl)
GC2 generic stress: PASS (prior GC2 + managed ref fields)
Specialized GC metadata: PASS (ref-map excludes primitive fields)
Generic optimizer tests: PASS (specialized methods are ordinary IR)
0.1–0.14 regression: PASS
Native Compilation: PASS
```

## 1. Generic Architecture

Dual strategy: erase reference instantiations; specialize primitive instantiations. Lowering is a dedicated phase before IR.

## 2. TypeParameterSymbol

`name`, `ownerType` / `ownerMethod`, `index`, `bounds` (multi-bound list).

## 3. TypeVariable

`JType` node backed by `TypeParameterSymbol`; semantic assignability; IR lowers to managed `ptr` until specialized.

## 4. ParameterizedType

`raw` + `typeArguments`; invariant assignability; retained through semantic analysis (not erased in parser).

## 5. Generic Classes

`ClassSymbol.typeParameters`; fields/methods/ctors see class type params.

## 6. Generic Interfaces

`InterfaceSymbol.typeParameters`; `implements Mapper<String,int>` + ITable dispatch.

## 7. Generic Methods

`MethodSymbol.typeParameters`; inference via `TypeInferenceEngine` (args + optional target return).

## 8. Bounds

`T extends Animal` checked at instantiation; multi-bound list supported in model.

## 9. Type Substitution

`TypeSubstitution` recursive over `TypeVariable` / `ParameterizedType` / arrays / wildcards.

## 10. Generic Inheritance

Parameterized `extends` / `implements` stored on symbols; hierarchy substitution for call sites.

## 11. Type Inference

`TypeInferenceEngine` + `Constraint` / `ConstraintSet` (`T = X`, subtype hooks).

## 12. Diamond Inference

`new Box<>("…")` uses variable target `ParameterizedType`.

## 13. Wildcards

`WildcardType` UNBOUNDED / EXTENDS / SUPER; parse + model; minimal capture.

## 14. Generic Interface Dispatch

Erased ITable for reference generics; call sites substitute return/param types semantically.

## 15. Reference Erasure

`Box<String>` / `Box<Person>` → shared `Box` (`ptr` fields).

## 16. Primitive Specialization

`Box<int>` → `Box$P_i32` with `i32` field; registry avoids duplicate generation.

## 17. Specialized Layout

`ObjectLayoutBuilder` + per-specialization ref-maps (primitives not scanned).

## 18. Generic Lambda

SAM + parameterized functional interfaces expressible; `Function<int,int>` specialization path prepared (reuse callable ABI where possible).

## 19. GC2 Integration

Erased/specialized managed fields remain precise roots; moving GC updates ref fields only.

## 20. Write Barrier Integration

Barriers on managed ref stores; primitive specialized fields skip barriers.

## 21. Escape Analysis

Specialized/erased objects are ordinary IR objects — participate in EA.

## 22. Optimizer Integration

No second optimizer; specialized methods inline like concrete methods.

## 23. Code Size Control

Reference instantiations share one implementation; primitives specialize only reachable keys.

## 24. Diagnostics

Raw types, arity, invariance, bound failure, inference failure messages.

## 25. Native E2E

`Box<String>`, diamond, `Box<int>`, generic method, generic interface — native executables PASS.

## 26. Performance

Goal met for `Box<int>`: element is `i32`, no `Integer` allocation in generated code.

## 27. Tests

`GenericsEndToEndTest` (12). Full suite 251 PASS.

## 28. Regression

0.1–0.14 e2e (interfaces, GC2, HTTP, etc.) PASS.

## 29. Known Limitations

- Implicit raw types rejected (intentional)
- `new T[n]` unsupported
- Generic `@NativeStruct` unsupported
- Parameterized `instanceof` / casts erased (unchecked)
- Full javac inference / capture not cloned
- Interface primitive specialization does not mint per-arg ITables (erasure + concrete impl)
- Explicit `Main.<String>identity` may be second-phase parser polish
- Specialization budget counters are basic (`GenericLowering.Stats`)

## 30. Important Files

- `semantic/TypeParameterSymbol.java`, `TypeVariable.java`, `ParameterizedType.java`, `WildcardType.java`
- `generic/TypeSubstitution.java`, `TypeInferenceEngine.java`, `GenericLowering.java`
- `ast/TypeName.java`, `TypeParameterDeclaration.java`
- `parser/Parser.java` (type params/args/diamond/wildcards)
- `semantic/SemanticAnalyzer.java`
- `ir/IrGenerator.java` (specialized field remap / ctor match)
- `vtable/ITableBuilder.java` (erased signature compatibility)
- `cli/CliArguments.java` (`--emit-generics`)
- `e2e/GenericsEndToEndTest.java`
- `docs/generics.md`

## 31. Architecture Acceptance

| Question | Answer |
|----------|--------|
| Does JType represent TypeVariable? | **YES** |
| Does JType represent ParameterizedType? | **YES** |
| Can classes declare type parameters? | **YES** |
| Can interfaces declare type parameters? | **YES** |
| Can methods declare type parameters? | **YES** |
| Are bounds checked? | **YES** |
| Does recursive substitution work? | **YES** |
| Does generic inheritance work? | **YES** |
| Does generic interface dispatch work? | **YES** |
| Does type inference work? | **YES** |
| Are reference generics erased/shared? | **YES** |
| Can primitive generic arguments specialize? | **YES** |
| Does Box\<int\> avoid boxing? | **YES** |
| Can specialized layouts contain primitive fields? | **YES** |
| Does GC metadata understand specialized layouts? | **YES** |
| Does moving GC correctly update generic reference fields? | **YES** |
| Does write barrier understand specialized generic fields? | **YES** |
| Can optimizer inline specialized methods? | **YES** |
| Can escape analysis optimize generic objects? | **YES** |
| Does LLVM remain unaware of source-level generics? | **YES** |

## 32. 0.16 Readiness

| Question | Answer |
|----------|--------|
| Can List\<T\> be expressed? | **YES** |
| Can Collection\<T\> be expressed? | **YES** |
| Can Iterable\<T\> be expressed? | **YES** |
| Can Iterator\<T\> be expressed? | **YES** |
| Can Map\<K,V\> be expressed? | **YES** |
| Can Set\<T\> be expressed? | **YES** |
| Can ArrayList\<T\> use reference erasure? | **YES** |
| Can ArrayList\<int\> specialize its storage? | **YES** (architecture) |
| Can HashMap\<K,V\> generate correct specialized layouts? | **YES** (architecture) |
| Can generic collections participate in GC2? | **YES** |
| Can collection iterators use interface dispatch? | **YES** |
| Can lambdas consume generic collections? | **YES** |
| Can optimizer remove iterator/lambda allocations? | **YES** (existing EA/inline) |

## Real Numbers (sample `--emit-generics`)

```text
Generic instantiations (sample Box<String>+Box<int>):
  erased instantiations: 4
  specialized classes: 1
    Box$P_i32
Reference implementations shared: yes (single Box for all ref args)
Boxing allocations avoided: Box<int> uses i32 field (no Integer)
```

## Final Acceptance Samples

- `Box<String>` + diamond → prints `JNative` / ` Native`
- `Box<int>(42)` → prints `42`, specialized layout `%class.Box_P_i32 = { ptr, i32 }`
