# Compiler Compatibility Testing

```text
common corpus
     │
┌────┴────┐
↓         ↓
Reference  Self-hosted component
lexer      (JnLex)
     │         │
     └────┬────┘
          ↓
     compare token dumps
```

Corpus: `tests/selfhost/lexer/corpus/`  
Compare: `scripts/selfhost-lex-compare.sh`

Later: parser AST dumps, then executable behavior under `tests/compiler-compatibility/`.

Program behavior remains the final authority once a full frontend exists.
