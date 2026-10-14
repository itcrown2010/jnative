# Compiler Components (Reference Implementation)

**Implementation language:** Java 21 (host)  
**Entry:** `io.jnative.Main` / `target/jnative.jar`  
**Host requirements:** JDK 21+, Maven, clang, LLVM toolchain (`jnative doctor`)

## Package map (`src/main/java/io/jnative/`)

| Component | Package / path | Language | Notes |
|-----------|----------------|----------|-------|
| Lexer | `lexer/` | Java | `Lexer`, `Token`, `TokenType` |
| Parser | `parser/` | Java | Large hand-written parser |
| AST | `ast/` | Java | Syntax tree nodes |
| Semantic / types | `semantic/` | Java | Analyzer, symbols, effects |
| IR | `ir/` | Java | High-level IR |
| Optimizer | `opt/` | Java | CFG / passes |
| Codegen | `backend/llvm/`, `backend/wasm/` | Java | LLVM IR emit, wasm |
| Linker | `backend/NativeLinker.java` | Java | Invokes clang |
| Runtime emit | `backend/llvm/runtime/` | Java | GC, threads, I/O glue |
| Build / project | `build/` | Java | `jnative.project` loader |
| CLI | `cli/` | Java | build/run/doctor/lsp/… |
| Stdlib inject | `stdlib/StdlibIncluder.java` | Java | Pulls `.java` stdlib resources |
| Diagnostics | `diagnostics/` | Java | Locations, messages |
| Format | `format/` | Java | Document formatter |
| LSP | `lsp/` | Java | Language server |

## Build process

```bash
mvn -q -DskipTests package   # → target/jnative.jar
java -jar target/jnative.jar <source> -o out
# or
java -jar target/jnative.jar build --project <dir>
```

## Generated / native artifacts

- LLVM IR (intermediate)  
- Host native executable via clang + linked runtime objects  
- Optional wasm32-browser outputs  

## Test coverage

Primary: `src/test/java/io/jnative/e2e/*EndToEndTest.java` plus unit tests under `src/test/java/`.

## Bootstrap boundary (Phase 6)

Safe to reimplement first in JNative: **lexer**, project/source utilities, diagnostics dumps.  
Keep in Java for now: LLVM backend, GC, linker, full semantic analyzer, parser (until lexer golden suite is green).
