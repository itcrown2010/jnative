# VS Code overview

**Status:** Official extension exists — `tools/vscode-jnative/` (VSIX line **0.8.x**)

The extension is a **first-class** workflow for desktop / web / mobile **container** projects. It is not a full IntelliJ replacement: LSP is diagnose-oriented (no rename/refactor suite).

| Capability | Notes |
|------------|-------|
| Language ID `jnative` | TextMate grammar + language config |
| Create / build / run / debug / package | Unified commands |
| Targets | `native`, `wasm32-browser` |
| Mobile | WebView containers + wasm — **not** full native UI |
| Debug | CodeLLDB for native; WASM/mobile LLDB **not supported** |

Upstream README: `tools/vscode-jnative/README.md`. Matrix: `SUPPORT_MATRIX.md`.
