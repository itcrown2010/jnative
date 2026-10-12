# JNative

作者：张晓达

**JNative** is an experimental ahead-of-time native compiler for a **subset** of the Java language.

## Licensing（公开前说明）

- 许可：**MIT**（[`LICENSE`](LICENSE)）——规定他人获得的**使用权**；不改变第三方组件自身版权。
- 原创代码版权：权利人**个人** [`张晓达`](COPYRIGHT.md)（非公司）；见 [`NOTICE`](NOTICE)、[`docs/legal/`](docs/legal/)。
- 第三方：[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)、[`third_party_licenses/`](third_party_licenses/)。
- 贡献：[`CONTRIBUTING.md`](CONTRIBUTING.md)（默认 PR **不**等于版权转让）。
- 材料索引：[`docs/legal/`](docs/legal/)。

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

**开发者文档站（中文优先，VitePress）**：见 [`website/`](website/README.md)。

**语言教程（循序渐进）**：[`docs/tutorial/`](docs/tutorial/index.md) · 示例 [`examples/tutorial/`](examples/tutorial/README.md)

```bash
cd website && npm install && npm run dev
# 静态构建：npm run build
# 语言教程验收：./scripts/docs/verify-tutorial.sh
# 文档站分阶段验收：./scripts/docs/verify-phase1.sh …
```

事实清单与阶段覆盖：[`docs/developer/`](docs/developer/README.md)。

内部/专题文档：

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
