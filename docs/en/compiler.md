# Compiler

## Invoking the compiler

After `mvn package`:

```bash
java -jar target/jnative.jar --help
java -jar target/jnative.jar --version
```

Common forms:

```bash
# Single source → native executable
java -jar target/jnative.jar path/to/Main.jn -o Main
java -jar target/jnative.jar path/to/Main.java -O2 -o Main

# Project build
java -jar target/jnative.jar build --project DIR

# Diagnostics
java -jar target/jnative.jar doctor
java -jar target/jnative.jar analyze file.jn --format json
```

Optimization levels: `-O0` … `-O3` (see [`../optimizer.md`](../optimizer.md)). Useful flags include `--opt-report`, `--emit-ir-before-opt`, `--emit-ir-after-opt`, `--verify-ir` where supported by your build.

---

## Pipeline stages (developer view)

1. **Parse** → AST  
2. **Semantic** → typed program + class metadata  
3. **IR build** → high-level IR  
4. **Opt** → IR passes (skipped/minimal at `-O0`)  
5. **LLVM** → bitcode/IR text + runtime linkage via `clang`

Key packages (Java compiler sources under `src/main/java/io/jnative/`): semantic analysis, layout, IR, `opt`, `backend.llvm`, runtime emitters.

---

## Targets

| Target | Status |
|--------|--------|
| Host native (macOS arm64 verified) | Primary |
| `wasm32-browser` | Experimental export for Web cores |
| Mobile | WebView shells around WASM/JS samples — not a separate ISA backend |

---

## Testing the compiler

- Unit / e2e: `src/test/java/` via `mvn test`  
- Prefer adding regression tests next to the feature you change  
- Native e2e depends on `clang` and host OS

See [CONTRIBUTING.md](../../CONTRIBUTING.md).
