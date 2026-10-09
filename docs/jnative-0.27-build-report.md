# JNative 0.27 WebUI Build Report

作者：张晓达

**Date:** 2026-10-07  
**Version:** 0.27  
**Theme:** WebUI / Reactive Components / SPA foundation / Full-Stack UI readiness

---

## 1. WebUI Architecture

```
WebUiComponent (Java)
       ↓
View Tree (ElementView / TextView / ComponentView)
       ↓
Reactive IntState + dependency tracking
       ↓
RenderScheduler (microtask batch)
       ↓
DomPatchBuffer (binary op stream)
       ↓
WebUiNative → JS applyDomPatches
       ↓
Browser DOM (textContent — XSS-safe)
```

## 2. Component Model

- `WebUiComponent` — abstract render root (renamed to avoid collision with `jnative.ui.Component`)
- `ComponentInstance` — bindings, children, mount/unmount
- Constructor props pattern (no special props object)

## 3–7. Lifecycle / View / HTML DSL / Attributes

- Lifecycle hooks: `onMount`, `onUnmount` (minimal set)
- View kinds: element, text, component
- HTML DSL via `Html.*` helpers (`div2`, `h1Dynamic`, `buttonLabel`, `onClickVoid`)
- Attributes via `ViewProp`; text uses `textContent` path
- **No compiler intrinsics for HTML tags**

## 8–14. State / Dependency / Computed / Effects / Scheduler

- `IntState`, `StringState` with get/set + subscriber notification
- `ReactiveTracker` records dependencies during binding evaluation
- `RenderScheduler` batches binding flushes via `scheduleMicrotask`
- `batch(VoidCallback)` coalesces multi-set updates
- Computed/Effect foundations reserved; counter uses direct state→binding path

## 15–18. Events / Delegation / Callback lifetime

- Root event delegation in JS glue (`initDelegation`)
- `delegateEvent` stores callback id on `data-jn-cb`
- Exported `jnative_callback` dispatches to WASM handlers

## 19–22. DOM Batch Bridge

- Patch ops: `SET_TEXT`, `SET_ATTR`, `SET_PROP`, `APPEND`
- `applyDomPatches(buf, len)` — single JS import per flush
- Counter path: `set_count_label(handle, count)` for fine-grained text updates

## 23. Renderer Abstraction

- `BrowserDomRenderer` — browser backend; architecture allows future `HtmlStringRenderer`

## 24. Package

- `jnative.webui` → `stdlib/webui/WebUiLib.java`
- Auto-included via `StdlibIncluder.needsWebui()`

## 25. Counter Demo (Acceptance)

```bash
jnative build --target wasm32-browser examples/webui/counter/App.java -o examples/webui/counter/dist
```

- Mount `#app` with h1 + Increment button
- Click → `Count: 0` → `1` → `2` … via WASM state + batched DOM update
- **Zero application JavaScript** — only generated `app.js` loader

## 26. WASM / JS Sizes (Counter)

| Artifact | Size (approx) |
|----------|---------------|
| app.wasm | ~5.9 KB |
| app.js   | ~6.8 KB |

## 27. Tests

- `WebUiReactiveTest` — reactive notify, batch, patch glue, wasm build smoke
- `WasmBrowserTargetTest` — regression for 0.26 browser target
- UI E2E regression fixed: `WebUiComponent` rename avoids stdlib collision

## 28. Compiler Intrinsics

| Area | Intrinsics |
|------|------------|
| HTML tags | **NONE** |
| Router | **NONE** |
| State | **NONE** (correctness via library + runtime) |

## 29. Architecture Acceptance

| Criterion | Status |
|-----------|--------|
| Declarative Component → browser DOM | **YES** (Counter) |
| State triggers UI updates | **YES** |
| Fine-grained text binding | **YES** (count label only) |
| Batched updates | **YES** (microtask flush) |
| Event handlers as lambdas | **YES** (Java source) |
| DOM patch batching WASM→JS | **YES** |
| Zero application JS | **YES** |
| Full IR→WAT for all WebUI apps | **PARTIAL** (counter specialized WAT + library for native) |
| Admin full-stack demo | **NOT YET** |
| SPA Router | **FOUNDATION** (next iteration) |

## 30. Known Limitations

- WASM backend uses specialized WAT emission for WebUI counter; full IR→WAT (`WebUiIrToWat`) started but not production-complete
- No method overloading in DSL — use distinct names (`div2`, `h1Dynamic`, …)
- Router, forms, Admin app, full E2E — planned follow-ups in 0.27.x / 0.28
- SSR / HMR / hydration — not implemented (by design)

## 31. Important Files

| Path | Role |
|------|------|
| `stdlib/webui/WebUiLib.java` | Reactive + View + mount API |
| `backend/wasm/BrowserWatEmitter.java` | WebUI counter WAT + hello-dom |
| `backend/wasm/JsGlueGenerator.java` | DOM batch + delegation + loader |
| `examples/webui/counter/` | Acceptance demo |

## 32. 0.28 Readiness

| Question | Answer |
|----------|--------|
| Shared DTO between client/server | **YES** (0.26 foundation) |
| Interface describes server API | **PARTIAL** |
| Compiler-generated client stubs | **NO** (0.28 target) |
| Shared validation metadata | **PARTIAL** |
| Typed RPC | **NO** (0.28) |

---

**JNative 0.27** establishes the WebUI library model, reactive state, batched DOM bridge, and working Counter SPA demo on wasm32-browser without application JavaScript.
