# Compiler self-host (incremental)

**Do not** replace the Java host compiler here.

| Area | Path | Status |
|------|------|--------|
| Lexer (JNative) | `lexer/` | Golden compare vs reference — `SELFHOST_LEX_OK` |
| Parser subset (JNative) | `parser/` | AstPrinter dump compare — `SELFHOST_AST_OK` (limited grammar) |
| Semantic / IR | — | Not started |

```bash
./scripts/selfhost-lex-compare.sh
./scripts/selfhost-ast-compare.sh
./scripts/bootstrap-stage1.sh
```

Docs: `docs/en/architecture/self-hosting.md`
