# Documentation Coverage Report

**Date:** 2026-10-13 (Phase 3 capability audit)  
**Scope:** English developer docs under `docs/en/`

## Coverage (qualitative %)

| Area | Coverage | Notes |
|------|----------|-------|
| Language | ~80% | Index + guides; not a full ISO-style spec |
| SDK reference | ~55% | Builtin File/HTTP/JSON/Thread pages added under `reference/sdk/` |
| Recipes | ~40% | Practical recipes for verified APIs only |
| Tutorials | ~65% | Notes app + prior HTTP/web/desktop/mobile |
| VS Code | ~90% | capabilities + empty-dir tutorial |
| Web / Desktop / Mobile matrices | ~85% | Honesty-first status labels |
| Capability / platform matrices | ~90% | Evidence-tagged |
| Package manager | ~25% | Still experimental CLI |

## Examples verified (smoke)

Script: `scripts/docs/verify-en-examples.sh`

| Example | Check |
|---------|--------|
| `examples/hello-world` | Run `Hello, JNative!` |
| `examples/Hello.java` | Run `Hello JNative` |
| `examples/file-io` | Round-trip file text |
| `examples/LambdaClosure.java` | Compiles |
| `examples/http-server` | Compile + curl |
| `examples/web/hello` | Compiles |
| `examples/json-demo` | String JSON round-trip |
| `examples/notes-cli` | create/read |
| `examples/multithreading` | counter `2000` |
| `examples/tcp-echo` | Compiles (manual nc smoke OK) |

## Phase 3 deliverables

- `docs/en/reference/capability-matrix.md`
- `docs/en/reference/platform-matrix.md`
- `docs/en/reference/example-index.md`
- `docs/en/reference/language/index.md`
- `docs/en/reference/sdk/*`
- `docs/en/recipes/*`
- `docs/en/web|desktop|mobile/capability-matrix.md`
- `docs/en/vscode/capabilities.md`, `tutorial.md`
- `docs/en/getting-started/what-can-i-build.md`
- `docs/en/tutorials/build-your-first-application.md`
- `docs/en/versions.md`, `changelog-policy.md`
- `docs/en/CAPABILITY_AUDIT_REPORT.md`
- `tools/check-docs`, `.github/workflows/docs.yml`

## Known gaps

1. `JsonNumber` / `putNumber` stringify smoke failure (emits `0`) — example uses strings  
2. Per-method docs for full Collections/Time/Db/Ui still incomplete  
3. CI docs job does **not** compile examples by default  
4. Linux/Windows host evidence still missing  

Full narrative: [CAPABILITY_AUDIT_REPORT.md](CAPABILITY_AUDIT_REPORT.md)
