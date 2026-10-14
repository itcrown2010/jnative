# Self-Hosting Status

**Date:** 2026-10-13 · Claim: **Self-hosting in progress** (not “self-hosted compiler”)

| Component | Existing (Java) | JNative | Compatible | Tests | Status |
|---|---|---|---|---|---|
| Lexer | Yes `lexer/Lexer.java` | Yes `compiler-selfhost/lexer` | Golden dump compare | `tests/selfhost/lexer` | Experimental |
| Parser | Yes | Subset `compiler-selfhost/parser` | AstPrinter dump compare (hello + fields/ctors) | `tests/selfhost/parser` | Experimental (subset) |
| AST | Yes | No | — | — | Host only |
| Semantic | Yes | No | — | — | Host only |
| IR | Yes | No | — | — | Host only |
| Backend | Yes | No | — | — | Host only |
| Compiler driver | Yes CLI | Partial tools | Studio/jn-project call host jar | verify scripts | Level 1–2 |
| Project model | Java `build/` + Studio Core | Yes CoreProject / jn-project | Manual | Studio verify | Experimental |

Bootstrap level: **1–2 demonstrated; 3 started**. See [phase6 report](../projects/phase6-bootstrap-report.md).
