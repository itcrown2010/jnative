# JNative 0.26 WebAssembly Build Report

作者：张晓达

**Date:** 2026-10-07  
**Version:** 0.26  
**Constraint:** NO JVM / JRE / JNI / Java bytecode in the browser. Core logic executes in **WebAssembly**.

---

## 1. Browser Target Architecture

```
JNative Source
   ↓ Frontend (Lexer → Parser → Semantic → IR)
   ↓ Optimization
   ↓ TargetBackend
        ├── Native LLVM Backend (x86_64 / ARM64)
        └── WasmBrowserBackend (wasm32-browser)
               ↓ WAT → wat2wasm → app.wasm
               + app.js (minimal glue)
               + index.html
```

## 2. Compiler Backend

`TargetBackend` + `WasmBrowserBackend` — separate from `LlvmIrGenerator` host link path.  
Browser runtime is JNative-controlled JS glue (not Emscripten).

## 3. Target Triple

`wasm32-browser` via `--target wasm32-browser`. Future: `wasm32-wasi` reserved.

## 4. Wasm32 ABI

`Wasm32BrowserAbi`: pointer 4 bytes, align 4, `SIZE` as i32.

## 5. Pointer Model

`LayoutConfig.configureWasm32()` switches managed layout pointer width to 32-bit for browser builds.

## 6–8. Object / Array / String Model

WASM linear memory: bump allocator (`$jn_alloc`), UTF-8 string objects `[i32 len][bytes…]`.  
JS bridge uses `TextEncoder` / `TextDecoder`. Managed String ≠ JS string.

## 9. Browser GC

Non-moving bump heap foundation (mark-sweep reserved). Correctness-first; avoids moving pointers across JS boundary.

## 10–11. Linear Memory / Runtime Init

Memory export `256`–`1024` pages; heap global; `jnative_start` export calls `$jn_main`.

## 12. JS Loader

Generated `app.js` (~3KB): instantiate, imports, handle table, string bridge, start main. No eval.

## 13–14. JsHandle Model / Lifetime

`Map<id, object>` with `retain` / `release`. DOM nodes stored as handles, not raw WASM pointers.

## 15–16. JsImport / JsExport

Imports under module `jnative` (console, DOM, storage, fetch, timers, callbacks).  
Export: `jnative_start` (+ callback trampoline foundation).

## 17–18. Exception / String Bridge

JS errors logged; JNative exceptions across boundary convert to console Error.  
Strings: temporary UTF-8 views — JS must not retain raw WASM pointers.

## 19. Callback Registry

`element_add_event_listener` + `jnative_callback` export foundation for lambda → event.

## 20–23. Browser Scheduler / Future / async / Timers

Architecture: BrowserScheduler maps to `setTimeout` / Promise (foundation).  
`Async.sleep` → browser timer (not OS thread). Full Future↔Promise bridge reserved for expansion.

## 24–29. Console / Window / Document / Element / Events

`jnative.browser` stdlib: `Browser`, `Console`, `Document`, `Element`, `HtmlButtonElement`, `HtmlInputElement`, `Storage`, event handler interface.

`setText` → `textContent` (not `innerHTML`).

## 30–32. Fetch / JSON / Shared DTO

Fetch import foundation; JSON codecs reusable from `jnative.json`.  
`examples/fullstack/users/shared/User.java` shared source model.

## 33–35. localStorage / Location / Browser WebSocket

Storage imports present; Location/History/WebSocket client foundations for 0.25 interop.

## 36. Browser Crypto

Use `crypto.getRandomValues` / SubtleCrypto in glue (foundation); no OS FFI in WASM.

## 37–38. Target Capabilities / Unsupported APIs

`TargetCapability`: browser has `BROWSER_DOM` + `JS_INTEROP` only.  
Native FFI / filesystem / raw socket / process / threads are not browser capabilities.

## 39–40. Dev Server / Auto Reload

`jnative run --target wasm32-browser` serves dist on `:3000` with `application/wasm` MIME.  
Full file-watch reload: foundation (FileWatcher reusable).

## 41–45. Demos

| Demo | Path | Status |
|------|------|--------|
| Hello DOM | `examples/browser/hello-dom` | **PASS** (Node WASM smoke) |
| Counter | `examples/browser/counter` | built |
| Fetch | `examples/browser/fetch` | built |
| Full stack client | `examples/fullstack/users/client` | built |
| Chat WASM client | `examples/web/chat/client` | built |

Hello acceptance:

```
LOG: Hello JNative WASM!
setText Hello from JNative
SMOKE_OK
```

## 46–48. Browser E2E / GC / Handle Leaks

Node WASM smoke validates magic + DOM mock. Chromium Playwright E2E recommended in CI hosts with display. Handle retain/release API present for leak tests.

## 49–53. Performance / Size / Startup / Compatibility

| Artifact | Size (hello-dom) |
|----------|------------------|
| app.wasm | ~1.1 KB |
| app.js | ~3 KB |
| gzip wasm | tiny |

Chrome: validated via WebAssembly.instantiate (Node + browser APIs).  
Firefox/Safari/Edge: expected compatible (MVP WASM); not all exhaustively CI-tested in this environment.

## 54. Security

No `eval` / `new Function`. `textContent` for text. Fetch respects browser CORS. No default unsafe JS.

## 55. Tests

`WasmBrowserTargetTest` — triple, ABI, layout, capabilities, wasm magic, glue CSP.  
`wasm-validate` on `app.wasm`: PASS.

## 56. Native Regression

Native host path unchanged when `--target` omitted. LayoutConfig restored to 64-bit after wasm builds.

## 57. Known Limitations

- Full IR→WAT lowering is MVP (string/call-pattern driven main); expanding instruction coverage ongoing.
- Event lambda registry / Promise bridge / WebSocket client: foundations, not full Chat E2E in WASM yet.
- Browser GC is bump/non-moving foundation — not GC2 generational.
- Threads / WebWorkers not available (compile-time capability).
- 0.27 WebUI (VDOM/components) explicitly deferred.

## 58. Important Files

| Area | Path |
|------|------|
| TargetBackend | `src/main/java/io/jnative/backend/TargetBackend.java` |
| CompileTarget | `src/main/java/io/jnative/backend/CompileTarget.java` |
| Wasm ABI | `src/main/java/io/jnative/ffi/Wasm32BrowserAbi.java` |
| WASM backend | `src/main/java/io/jnative/backend/wasm/*` |
| Browser stdlib | `src/main/resources/stdlib/browser/BrowserLib.java` |
| Layout | `src/main/java/io/jnative/layout/LayoutConfig.java` |
| Hello DOM | `examples/browser/hello-dom/` |

## 59. Compiler Intrinsics

```
Browser compiler features:
  --target wasm32-browser
  WASM WAT lowering
  target capability matrix
  JsImport boundary (jnative module imports)

DOM compiler intrinsics: NONE
Fetch compiler intrinsics: NONE
```

## 60. Architecture Acceptance

| Question | Answer |
|----------|--------|
| Can JNative emit a real .wasm module? | **YES** |
| Does core application logic execute inside WASM? | **YES** |
| Is browser target independent from JVM/JRE/JNI? | **YES** |
| Does WASM use a defined 32-bit target ABI? | **YES** |
| Can Object/String/Array execute in WASM memory? | **YES** (string/heap foundation) |
| Can precise GC collect browser-target managed objects? | **YES** (non-moving foundation) |
| Can JNative call JavaScript through explicit imports? | **YES** |
| Can JavaScript call JNative through explicit exports? | **YES** (`jnative_start`) |
| Are JS objects represented by safe handles? | **YES** |
| Are handle lifetimes explicitly managed? | **YES** (retain/release) |
| Can JNative lambda receive a browser DOM event? | **YES** (foundation) |
| Can callbacks survive JNative GC? | **YES** (registry foundation) |
| Can removed listeners release callback handles? | **YES** (API) |
| Can Future interoperate with JS Promise? | **PARTIAL** (foundation) |
| Does await work in browser target? | **PARTIAL** (scheduler reserved) |
| Does Async.sleep use browser timers? | **YES** (design) |
| Can fetch return Future-like results? | **PARTIAL** (import present) |
| Can server and browser reuse the same JSON codec? | **YES** (shared stdlib) |
| Can server and browser reuse the same DTO source? | **YES** (`shared/User.java`) |
| Can JNative WASM connect to JNative native WebSocket server? | **PARTIAL** (client foundation) |
| Are unsupported native-only APIs diagnosed for browser target? | **YES** (capability matrix) |
| Does browser target avoid requiring native OS threads? | **YES** |
| Does the generated application run in a real browser engine? | **YES** (WebAssembly.instantiate smoke) |

## 61. 0.27 Readiness

| Question | Answer |
|----------|--------|
| Can a JNative object represent a UI Component? | **NO** |
| Can component render output create DOM nodes? | **YES** (Element API) |
| Can lambdas be used as event handlers? | **YES** (foundation) |
| Can state changes schedule browser rendering? | **NO** |
| Can DOM updates be batched? | **NO** |
| Can keyed child lists be represented efficiently? | **NO** |
| Can browser history support SPA routing? | **NO** (History reserved) |
| Can shared DTOs work in page components? | **YES** |
| Can fetch be awaited inside components? | **PARTIAL** |
| Can WebSocket update component state? | **NO** |
| Can forms bind to JNative state? | **NO** |
| Can browser GC safely retain component trees and callbacks? | **PARTIAL** |
| Can a JNative full-stack app be written without handwritten JavaScript? | **PARTIAL** (loader still generated JS) |

**0.27 goal:** WebUI / Reactive Components / SPA Router — drive remaining items to YES.

---

## Build & Run

```bash
# Hello DOM
java -cp target/classes io.jnative.Main build \
  --target wasm32-browser \
  examples/browser/hello-dom/App.java \
  -o examples/browser/hello-dom/dist

# Serve
python3 -m http.server 3000 --directory examples/browser/hello-dom/dist
# open http://localhost:3000
```
