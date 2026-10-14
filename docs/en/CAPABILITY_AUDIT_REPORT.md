# JNative Capability Audit

**Date:** 2026-10-13  
**Method:** Source + e2e tests + runnable examples. No credit for class names / TODOs / roadmap alone.  
**Host evidence:** macOS arm64 · CLI `JNative 0.32`

## Summary blocks

```text
JNative Capability Audit

Language: Java-like subset AOT; classes/inheritance/interfaces/generics/lambdas/exceptions/arrays/strings/control-flow implemented with e2e. No full Java SE / reflection.
SDK: File, TCP, HTTP, JSON, collections, time, FFI, UI, web modules present; maturity mostly Experimental. JsonNumber stringify gap found during audit.
Runtime: GC2 + native threads/Mutex/atomics (native only). wasm single-threaded.
CLI: compile/build/new/doctor Stable on verified host; test/lsp/packages Experimental.
Package Manager: commands exist (add/publish/…); sparse end-to-end docs — Experimental.
VS Code: 0.8.x highlighting + build/run + LSP navigation/rename/format + LLDB MVP — Experimental overall.
Web: Native HttpServer/Client Experimental; WebApp Experimental; browser wasm Experimental; WebSocket Not Supported.
Desktop: Native exe Stable (macOS arm64); GUI Experimental macOS; Win/Linux unverified.
Mobile: WebView+wasm containers Experimental — NOT a production native UI toolchain.
Browser: wasm32-browser Experimental; DOM via JS shell; WASM fetch_get Not Supported.

Implemented: core language, native console toolchain (macOS arm64), File builtins, TCP/HTTP stdlib, Thread/Mutex, JSON parse/stringify (strings), FFI samples, vscode grammar.
Experimental: most stdlib depth, TLS/HTTPS, UI, wasm, mobile shells, LSP, packages, WebApp.
Partial: Windows FS, static files/templates, directory walk recipes, JsonNumber formatting, desktop packaging.
Planned: production auth/DB hardening, native mobile UI (if ever), broader CI OS matrix — see roadmap (not claimed implemented).
Unsupported: Java SE completeness, reflection-as-product, WebSocket product API, equating FFI with mobile apps, byte-identical reproducible builds as a promise.

Documentation coverage: English tree under docs/en/ substantially present; Phase 3 added matrices, SDK reference sketches, recipes, VS Code tutorial, what-can-i-build, versions, audit report. Per-method SDK completeness still incomplete vs full stdlib.
Example coverage: hello/file/http/web + new notes/json/thread/tcp-echo. Many large samples (sudoku, guide-tasks) exist; webui fragile.
Test coverage: Broad e2e suite under src/test/java/io/jnative/e2e/ (HTTP, File, Thread, Network, Generics, GC, FFI, …). Full mvn test not re-run as part of this doc-only pass.

Missing documentation:
- Exhaustive per-method pages for all Collections/Time/Db/Ui APIs
- Linux/Windows install verification pages
- Package publish E2E tutorial

Missing examples:
- HTTPS-focused README sample
- Generics/inheritance dedicated project READMEs (source demos exist)
- Directory-walk once IoLib API confirmed for tutorial use

Documentation inconsistencies:
- pom version vs CLI 0.32 vs help 1.0-dev (documented in versions.md)
- Older FACT_INVENTORY VS Code “no completion” vs extension 0.8 README (LSP completion exists) — English vscode/capabilities.md is authoritative for 0.8.x
- sdk/files.md still points at narrative file-io; reference/sdk/filesystem.md lists builtin signatures

Implementation gaps discovered:
1. JsonObject.putNumber / JsonNumber stringify emitted 0 in smoke tests on 0.32 (recorded; example uses strings).
2. No mkdir builtin on File — notes demo uses flat CWD files.
3. webui full Html path historically fails WAT — still not tutorial-ready.

Recommended next steps:
1. Fix JsonNumber formatting + add regression e2e; restore numeric json-demo.
2. Extend scripts/docs/verify-en-examples.sh + CI docs job (tools/check-docs).
3. Extract stdlib signatures automatically into docs/en/reference/sdk/.
4. Add Linux CI host evidence before claiming Linux Stable.
5. Keep mobile docs explicit: WebView+wasm only.
```

### Fact vs plan

| Kind | Examples |
|------|----------|
| **Facts** | File builtins signatures; HttpLib classes; Thread e2e; vscode 0.8.1 commands; TargetProfiles wasm vs native |
| **Plans / not claimed** | Native mobile UI SDK; WebSocket; production auth suite |

Primary matrices: [capability-matrix](reference/capability-matrix.md), [platform-matrix](reference/platform-matrix.md).
