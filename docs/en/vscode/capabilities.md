# VS Code Capabilities

**Extension:** `tools/vscode-jnative/` · version **0.8.1** (`package.json`)  
**Evidence:** extension source + README (host UI not re-verified in every audit pass)

| Capability | Supported | Status |
|---|---:|---|
| Syntax highlighting (TextMate) | Yes | Stable (grammar) |
| Language ID `.jn` / `.java` as jnative | Yes | Stable |
| Project detection (`jnative.project` / `jnative.toml`+`src/`) | Yes | Stable (logic) |
| Build task / Build project | Yes | Experimental |
| Run project | Yes | Experimental |
| Problems from JSON diagnostics | Yes | Experimental |
| LSP (`jnative lsp`) diagnostics | Yes | Experimental |
| Code completion (semantic) | Yes | Experimental |
| Signature help | Yes | Experimental |
| Go to definition | Yes | Experimental |
| Find references | Yes | Experimental |
| Hover / Outline / Workspace symbols | Yes | Experimental |
| Rename (F2) | Yes | Experimental |
| Document formatting | Yes | Experimental |
| Range formatting | No | Not Supported |
| Debug (CodeLLDB MVP) | Yes | Experimental |
| Create project wizards | Yes | Experimental |
| Workspace Trust gating | Yes | Implemented |
| Fixed Java API completion lists | No | Not Supported (by design) |

### Limits (from extension README)

- Completion/navigation use compiler symbols, not a JDK stub list.  
- Rename/references skip strings/comments; third-party read-only sources refused.  
- Untrusted workspaces: highlighting only—no compiler/LSP/build/run.

See [overview](overview.md), [installation](installation.md), [debugging](debugging.md).
