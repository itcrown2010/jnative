# Frontend / Backend Boundary

Target architecture (incremental):

```text
                 ┌───────────────────┐
                 │ JNative Source    │
                 └─────────┬─────────┘
                           │
             ┌─────────────▼─────────────┐
             │ Frontend (future JNative) │
             │ Lexer / Parser / Semantic │
             │ IR Generation             │
             └─────────────┬─────────────┘
                           │
                           ▼
                    JNative IR
                           │
                           ▼
              Existing Backend (Java host)
                           │
                           ▼
                  Native Executable
```

**Today:** Frontend and backend both live in the Java host compiler. Phase 6 ports the **lexer** to JNative for differential testing only — it does **not** yet replace the host lexer in the compile pipeline.
