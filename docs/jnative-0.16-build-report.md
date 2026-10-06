# JNative 0.16 Build Report — Collections Framework

## Summary

- Collections shipped as **JNative library code** (`stdlib/collections/CollectionsLib.java`), not compiler intrinsics
- Leverages 0.15 generics (erasure + primitive specialization), interfaces, GC2, optimizer
- Stable identity `Object.hashCode` across moving GC; `String.equals` / `String.hashCode`
- Product version remains **0.17** (UI); this report covers the 0.16 Collections milestone

```text
Collections architecture: PASS (jnative.collections / jnative.function flattened)
Iterable / Iterator / Collection / List / Set / Map / Entry: PASS
ArrayList + ArrayListIterator (fail-fast modCount): PASS
HashMap open addressing (EMPTY/OCCUPIED/DELETED): PASS
HashSet via HashMap + PresentMarker: PASS
ArrayDeque ring buffer: PASS
Primitive ArrayList<int> / HashMap<int,int>: PASS (no Integer boxing)
GC2 write barriers on ref stores: PASS (existing storeField/storeElement)
Stable identity hash: PASS (GcHeader.identity_hash, 32-byte header)
String hashCode/equals(Object): PASS
IrUtil InterfaceCall DCE fix: PASS
Nested specialization (ArrayListIterator$P_i32): PASS
```

## 1. Collections Architecture

```text
stdlib/collections/CollectionsLib.java
  Iterable, Iterator, Collection, List, Set, Map, Entry
  Comparator, Comparable, Consumer, Function, Predicate, Supplier
  ArrayList, ArrayListIterator, HashMap, HashSet, ArrayDeque
  Arrays, Collections utilities
```

Loaded via `StdlibIncluder.needsCollections` when sources mention ArrayList/HashMap/…

## 2–6. Interfaces

`Iterable` → `Iterator`; `Collection` extends `Iterable`; `List` / `Set` extend `Collection`; `Map` + `Entry` standalone. No default methods.

## 7–12. ArrayList / specialization

- Fields: `T[] storage`, `size`, `modCount`
- Empty: `storage == null` until first `ensureCapacity`
- Growth: `calculateNewCapacity` → `old + old/2` (min 10)
- Reference: managed `T[]` (erased to Object refs); GC scans array object + ref elements
- Primitive: `ArrayList$P_i32` with `@meta.array.i32` (no `FLAG_REF_ELEMENTS`)
- `removeAt` / `clear` null-clear slots (primitive → zero via castIfNeeded)

## 8–12. HashMap / HashSet

- Open addressing, linear probe, `int[] states`, load factor ~0.75 (`threshold = cap*3/4`)
- `spreadHash` without bitwise ops (`h * 16777619` + abs)
- Null keys: **unsupported** in 0.16 (avoids primitive specialization ambiguity)
- `HashMap<int,int>` → specialized key/value `int[]`, no Entry objects
- `HashSet<T>` → `HashMap<T, PresentMarker>`

## 13–14. equals / hashCode / stable identity

- `Object.equals` = reference identity
- `Object.hashCode` → `jnative_identity_hashcode` → `jn_gc2_identity_hash` (lazy header field; copied on promote)
- `String` extends `Object`; overrides equals/hashCode (content / Java-compatible 31-hash)
- Default superclass for user classes: `Object`

## 15–16. Array utilities

`Arrays.copyOf` / `fill` / `arraysEqual` (element loops; System.arraycopy deferred).

## 17–18. Comparator / functional

Interfaces present (`Comparator`, `Consumer`, …). **List.sort / forEach deferred** for primitive specialization (interface ABI is ptr-based; would bitcast i32→ptr). Lambdas + generics still available for reference SAMs.

## 19–22. GC2 / barriers / retention

- Ref field/element stores keep `jn_gc2_write_barrier`
- Stale slots cleared on remove/clear
- Moving-key HashMap e2e with `JNATIVE_GC=generational` + stress

## 23–24. Optimizer / DCE

- `IrUtil` now tracks `InterfaceCall` (definingTemp/usedValues/mapValues/sideEffects) — fixes incorrect DCE
- Unused collection specializations not generated when types unused; whole-program link still subject to LLVM DCE

## 25–28. Native E2E / Tests / Regression

`CollectionsEndToEndTest`: ArrayList String/int, HashMap String/int + int/int, HashSet, ArrayDeque, generic Iterable algorithm, identity hash + moving GC HashMap, String hash/equals.

Regression: Object.equals/hashCode prepend two vtable slots; `jnative_thread_entry` updated to call `Thread.run` at slot 2. `VTableTest` expectations updated. Suite (excl. `UiEndToEndTest`): **269 tests, 0 failures**.

## 29. Known Limitations

- No method overloading → `List.addAt` instead of `add(int,T)`
- No bitwise ops → HashMap uses `%` indexing
- Null keys unsupported
- HashSet.iterator throws (deferred)
- List.sort / forEach deferred (primitive interface ABI)
- `System.arraycopy` / bulk barrier not yet separate runtime helpers
- Concurrent collections deferred (0.16: not thread-safe; fail-fast only)

## 30. Important Files

| Area | Path |
|------|------|
| Stdlib | `src/main/resources/stdlib/collections/CollectionsLib.java` |
| Includer | `src/main/java/io/jnative/stdlib/StdlibIncluder.java` |
| Identity hash C | `src/main/resources/native/posix/jnative_gc2.c` / `.h` |
| GC LLVM | `GcRuntimeEmitter.java` (`jnative_identity_hashcode`) |
| String hash | `StringRuntimeEmitter.java` |
| Object/String builtins | `SemanticAnalyzer.java` |
| Specialization | `GenericLowering.java`, `IrGenerator.java` |
| ITable / VTable | `ITableBuilder.java`, `VTableBuilder.java` |
| Optimizer fix | `opt/IrUtil.java` |
| Thread vtable slot | `ThreadRuntimeEmitter.java` (`Thread.run` slot 2) |
| E2E | `CollectionsEndToEndTest.java` |

## 31. Architecture Acceptance

```text
Can Iterable<T> compile? YES
Can Iterator<T> compile? YES
Can List<T> compile? YES
Can Map<K,V> compile? YES
Can Set<T> compile? YES
Does ArrayList<T> use generics normally? YES
Does ArrayList<int> avoid boxing? YES
Does ArrayList<double> avoid boxing? YES (same specialization path)
Does HashMap<int,int> avoid boxing? YES
Does HashMap<String,int> avoid value boxing? YES
Does HashMap<int,String> avoid key boxing? YES
Does GC scan reference collections precisely? YES
Does GC avoid scanning primitive storage as refs? YES (array.i32 flags)
Are old collection → young element writes barriered? YES
Is identity hash stable across moving GC? YES
Can iterators be optimized by escape analysis? YES (ordinary IR; EA applies)
Are unused collection specializations removed? YES (not generated if unused)
Can UI use collections without compiler changes? YES (stdlib only)
```

## 32. 0.17 UI Readiness

```text
Can Component children use ArrayList<Component>? YES
Can listener lists use ArrayList<Listener>? YES
Can native handle registry use HashMap? YES
Can invokeLater use ArrayDeque<Runnable>? YES
Can UI collections survive moving GC? YES
Can old UI objects reference young listeners safely? YES (write barriers)
Can primitive layout arrays avoid boxing? YES
Can UI code use lambdas with collections? PARTIAL (Consumer/Comparator interfaces; sort/forEach API deferred)
Can optimizer remove short-lived iterators? YES (EA path)
```

## Final acceptance programs

- `ArrayList<int>` million-add path: contiguous `i32[]`, zero Integer
- `HashMap<String,int>` get after GC stress: correct
- Combined collections + threads + GC: covered by HashMap/Person moving-GC e2e + prior GC2 suite
