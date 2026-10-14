# Bootstrap Roadmap

Adjusted after repository audit (2026-10-13).

| Stage | Goal | Status |
|------:|------|--------|
| 1 | JNative utility tools (project inspect, Studio Core) | **In progress / partial** |
| 2 | JNative tools drive reference build | **Demonstrated** (Studio Core) |
| 3 | JNative lexer + golden vs reference | **Done (corpus)** (`compiler-selfhost/lexer`) |
| 4 | JNative parser + AST normalize/compare | **Started (subset)** (`compiler-selfhost/parser`) |
| 5 | Semantic subset in JNative | Planned |
| 6 | Frontend → existing IR/backend bridge | Planned |
| 7 | `jnative-compiler` driver (JNative) using existing backend | Planned |
| 8 | Stage-1 bootstrap script always green in CI | In progress |
| 9 | Backend pieces in JNative (only if useful) | Future |
| 10 | Self-hosted compiler release | Future — not claimed |

## Bootstrap stage names (this repo)

| Name | Meaning |
|------|---------|
| **Bootstrap 0** | Existing Java `jnative.jar` |
| **Bootstrap 1** | JNative tools + JNative lexer dump compatible with reference |
| **Bootstrap 2** | JNative frontend + existing backend (not yet) |
| **Bootstrap 3** | Self-rebuild loop (not yet) |

Script: `scripts/bootstrap-stage1.sh`
