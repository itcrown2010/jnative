# Language Reference Index

Authoritative guides for **implemented** language surface. Depth lives in `docs/en/language/`; this index is the map.

Only features with compiler + tests (or verified examples) appear as numbered sections. Unsupported Java SE topics are listed under “Out of scope”.

## Contents

1. [Lexical structure & syntax](../../language/syntax.md)
2. [Keywords / overview](../../language/overview.md)
3. [Variables](../../language/variables.md)
4. [Types](../../language/types.md)
5. [Operators & expressions](../../language/operators.md)
6. [Statements & control flow](../../language/control-flow.md)
7. [Functions & methods](../../language/functions.md)
8. [Classes](../../language/classes.md)
9. [Inheritance](../../language/inheritance.md)
10. [Interfaces](../../language/interfaces.md)
11. [Generics](../../language/generics.md)
12. [Arrays](../../language/arrays.md)
13. [Strings](../../language/strings.md)
14. [Exceptions](../../language/exceptions.md)
15. [Lambdas](../../language/lambdas.md)
16. [Method references](../../language/method-references.md)
17. [Concurrency](../../language/concurrency.md)
18. [Native interoperability (FFI)](../../sdk/ffi.md)

Also: [Language reference overview](../language-reference.md) · [Capability matrix](../capability-matrix.md)

## Out of scope (not authoritative sections)

- Full Java reflection / dynamic proxies  
- Arbitrary JAR consumption / Java SE library compatibility  
- Synchronized methods / `volatile` as in HotSpot (use JNative `Mutex` / atomics patterns)  

When adding a language feature: update a page here, add e2e under `src/test/java/io/jnative/e2e/`, and a runnable example.
