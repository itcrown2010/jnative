# JNative

**JNative** is an experimental ahead-of-time native compiler for a **subset** of the Java language.

```text
Java Source
  → Lexer → Parser → AST → Semantic → JNative IR → LLVM IR → clang
  → Native Executable
```

Generated programs do **not** require a JVM/JRE at runtime.

> **Important:** JNative is **not** a complete Java SE implementation.
> Current release: **0.5 — Native Garbage Collector**.

## Requirements

- Java 21+
- Maven 3.9+
- `clang` on `PATH`

## Build

```bash
mvn package
mvn test
```

## Use

```bash
java -jar target/jnative.jar examples/Hello.java -o Hello
./Hello
```

GC env (optional):

```bash
JNATIVE_GC_STRESS=1 JNATIVE_GC_STATS=1 ./Hello
```

## What's new in 0.5

- Precise stop-the-world mark-sweep GC (non-moving, single-threaded)
- GC header + allocation registry
- Shadow-stack precise roots
- `System.gc()` intrinsic
- Stress / stats via environment variables

## Documentation

- [Architecture](docs/architecture.md)
- [Language support](docs/language-support.md)
- [Object model](docs/object-model.md)
- [GC](docs/gc.md)
- [Memory model](docs/memory-model.md)
- [Runtime](docs/runtime.md)
- [Constructors](docs/constructors.md)
- [Arrays](docs/arrays.md)
- [String](docs/string.md)
- [RTTI](docs/rtti.md)
- [Inheritance](docs/inheritance.md)
- [VTable](docs/vtable.md)

## License

MIT — see [LICENSE](LICENSE).
