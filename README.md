# JNative

作者：张晓达

**JNative** is an experimental ahead-of-time native compiler for a **subset** of the Java language.

```text
Java Source
  → Lexer → Parser → AST → Semantic → JNative IR → LLVM IR → clang
  → Native Executable
```

Generated programs do **not** require a JVM/JRE at runtime.

> **Important:** JNative is **not** a complete Java SE implementation.
> Current release: **0.12 — Optimizing Compiler**.

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

java -jar target/jnative.jar examples/LambdaClosure.java -o LambdaClosure
./LambdaClosure
```

## What's new in 0.12

- IR optimization pipeline (`-O0`…`-O3`, default **-O2**)
- Scalar replacement for non-escaping primitive objects (e.g. `Point`)
- Escape analysis, devirtualization (exact-type), constant folding, DCE
- Null/bounds/safepoint check elimination (conservative)
- `--opt-report`, `--emit-ir-before-opt`, `--emit-ir-after-opt`, `--verify-ir`
- Docs: [optimizer.md](docs/optimizer.md), [0.12 build report](docs/jnative-0.12-build-report.md)

## What's new in 0.11

- `EffectAnalyzer` — bitset effects, call-graph propagation, contract verification
- `@NoGC`, `@NoThrow`, `@NoBlock`, `@Deterministic`, `@Native(effects=...)`
- `--emit-effects` report; `@NoGC` omits loop safepoints + runtime alloc guard
- `Mutex.tryLock()`

## What's new in 0.10

- Lambdas, closures, and method references (SAM callables)
- `Native.callback()` — export managed SAMs as C function pointers
- `HttpServer.get()` / `serveOne()` route dispatch
- `CompilerOptions.extraNativeSources` for custom C linkage in tests/tools
- Examples: `LambdaClosure.java`, `NativeCallback.java`

## What's new in 0.9

- Multithreading: `Thread`, `Mutex`, `Condition`, `AtomicInt`/`AtomicLong`/`AtomicBool`
- STW GC with multi-thread root scan

## Documentation

- [Optimizer](docs/optimizer.md)
- [Effects](docs/effects.md)
- [@NoGC](docs/no-gc.md)
- [@Deterministic](docs/deterministic-code.md)
- [Native effect contracts](docs/native-effect-contracts.md)
- [Callables / Lambdas](docs/callable.md)
- [Native callbacks](docs/native-callback.md)
- [HTTP](docs/http.md)
- [Networking](docs/networking.md)
- [File I/O](docs/file-io.md)
- [Architecture](docs/architecture.md)
- [Language support](docs/language-support.md)
- [FFI](docs/ffi.md)
- [Native struct](docs/native-struct.md)
- [GC](docs/gc.md)
- [Exceptions](docs/exceptions.md)
- [Threading](docs/threading.md)

## License

MIT — see [LICENSE](LICENSE).
