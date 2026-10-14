# Phase 5 Engineering Report — JNative Studio

## Capability Audit (before)

File I/O, directory list, ProcessBuilder, JSON project files, and a StudioLib GUI existed in stdlib/examples — but **GUI Studio failed to compile** (UiLib vs GraphicsLib duplicate Point/Size/Rect).

## New Capabilities

No new compiler semantics required. Delivered:

- Layered **Studio Core CLI** in JNative (`examples/jnative-studio`)
- Dogfood project `examples/hello-app`
- Diagnostic line parser for real compiler output
- Feasibility, projects gallery, release notes, distribution notes, benchmarks stub

## JNative Studio — what works

Open / tree / read / write / build / run / problems on macOS arm64. Verified by `scripts/verify_core.sh` → `STUDIO_CORE_OK`.

## Tests

| Kind | Item |
|------|------|
| Dogfood script | `examples/jnative-studio/scripts/verify_core.sh` |
| Existing e2e | `StudioEndToEndTest` (Process, Files.list, Project, headless Studio when stack builds) |
| Manual fail-build | Diagnostics show `Main.jn:3:5` undefined method |

## Dogfooding

**Yes** — Studio Core built `hello-app` and ran `Hello from JNative Studio!` with exit 0.

## Self-Hosting level

**Level 3** (Studio in JNative; edits projects; builds projects). Level 4 partial (builds itself as Core; GUI entry not buildable).

## Blockers

1. UiLib + GraphicsLib duplicate `Point`/`Size`/`Rect`  
2. GUI Studio / notepad-class apps blocked until (1)  
3. Linux/Windows Studio Core unverified  

## Next Phase

1. Fix graphics/UI geometry types (single shared module)  
2. Re-enable `examples/studio` GUI against Core services  
3. Add Linux CI dogfood for Studio Core  
4. Optional: package Studio Core without requiring developers to pass `--jar` via env discovery  
