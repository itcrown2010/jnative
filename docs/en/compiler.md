# Compiler

The JNative compiler is a Java program (`target/jnative.jar`). It turns project or single-file sources into a **native executable** (or experimental WASM). This page is a practical map of how to drive it and where the code lives.

---

## Install the toolchain first

You need Java 21+, Maven, and `clang` — see [getting-started.md](getting-started.md).

```bash
mvn -q -DskipTests package
java -jar target/jnative.jar --version   # e.g. JNative 0.32
java -jar target/jnative.jar doctor
```

---

## Everyday commands

```bash
# Single file → executable (default -O2)
java -jar target/jnative.jar path/to/Main.jn -o Main
./Main

# Optimization / inspection
java -jar target/jnative.jar Main.java -O0 -o Main
java -jar target/jnative.jar Main.java -O2 --opt-report
java -jar target/jnative.jar Main.java --emit-ir-after-opt
java -jar target/jnative.jar Main.java --debug -O0 -o Main   # DWARF + source mapping

# Frontend only (no link)
java -jar target/jnative.jar analyze Main.jn --format json

# Project
java -jar target/jnative.jar new demo
java -jar target/jnative.jar build --project demo
```

Full flag surface: `java -jar target/jnative.jar --help`.

---

## What happens on `compile`

Orchestrated by `JNativeCompiler.compile`:

1. Configure pointer/layout (`LayoutConfig`) for `native` or `wasm32-browser`.
2. Reject APIs that the **target profile** cannot support.
3. Load stdlib + user units; lex/parse/typecheck (`MultiUnitFrontend`).
4. Optionally run annotation processors and/or RPC generators, then re-analyze.
5. Run `EffectAnalyzer`, then `GenericLowering`.
6. Build IR (`IrGenerator`), run `AsyncLoweringPass` if needed.
7. Run `OptimizationPipeline` when `-O` > 0.
8. Either:
   - **native:** emit LLVM IR → verify → `clang` link with runtime objects, or
   - **wasm32-browser:** emit WASM + JS under an output directory.

Project builds (`jnative build`) go through `CompilerSession` / incremental machinery with their own default opt profile (`-Odev` / release); single-file options default to **`-O2`**.

---

## Important modules

| Concern | Start here |
|---------|------------|
| CLI parsing | `io.jnative.cli` |
| Pipeline driver | `io.jnative.compiler.JNativeCompiler` |
| Options | `io.jnative.compiler.CompilerOptions` |
| Frontend | `lexer`, `parser`, `semantic`, `MultiUnitFrontend` |
| IR / opt | `ir`, `opt` |
| LLVM | `backend.llvm`, `backend.llvm.runtime.*` |
| Link | `backend.NativeLinker`, `backend.Toolchain` |
| WASM | `backend.wasm` |

---

## Testing changes

```bash
mvn -Dtest=EndToEndTest,ExceptionEndToEndTest test
# or a single class under src/test/java/io/jnative/e2e/
```

Prefer an e2e that compiles and runs a tiny program whenever you change observable language or runtime behavior. Host `clang` is required for native e2e.

See [CONTRIBUTING.md](../../CONTRIBUTING.md) and [architecture.md](architecture.md).
