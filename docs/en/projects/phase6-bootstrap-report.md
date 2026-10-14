# Phase 6 Bootstrap Report

## Bootstrap Level

**Levels 1–2 demonstrated; Level 3 lexer complete (corpus); Level 3–4 parser subset started.**  
Claim language: **Self-hosting in progress** — not “self-hosted compiler”.

## Components Implemented in JNative

| Component | Path |
|-----------|------|
| JnLex lexer + dump CLI | `compiler-selfhost/lexer/` |
| JnAst subset parser + dump CLI | `compiler-selfhost/parser/` |
| jn-project inspect tool | `examples/jn-tools/jn-project/` |
| Studio Core (prior) | `examples/jnative-studio/` |

## Compatibility

| Suite | Result |
|-------|--------|
| Lexer golden (`tests/selfhost/lexer/corpus` ×6) | `SELFHOST_LEX_OK` |
| Parser AST dump (`tests/selfhost/parser/corpus` ×2) | `SELFHOST_AST_OK` |
| Full parser / semantic / IR | Not started |
| Shared language-conformance layout | Scaffold only |

## Bootstrap Process

```bash
./scripts/bootstrap-stage1.sh
```

Prints `STAGE1_PASS` when tools + lexer compare + sample compile succeed.

## Performance

Not compared yet — correctness first. No old-vs-new compile-time claims.

## Known Limitations

- Self-hosted lexer is **not** wired into the compile pipeline  
- No JNative parser/semantic/IR yet  
- CI job uses macOS (needs clang); Linux unverified for Stage 1  
- Reference compiler remains Java host  

## Next Step

1. Grow parser subset (if/while/for, generics, arrays, lambdas) toward AstPrinter parity  
2. Keep existing backend; do not rewrite LLVM  
3. Only after broad AST parity: semantic subset + IR bridge  
