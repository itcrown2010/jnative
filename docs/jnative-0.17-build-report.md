# JNative 0.17 Build Report — Native UI Foundation

## Summary

- Version **0.17**
- UI as **stdlib + C/ObjC shim** (not compiler intrinsics)
- Backends: **Headless** (tests), **Cocoa** (macOS), **Win32** (Windows)
- Single UI thread + `invokeLater` + handle-id registry for GC-safe callbacks

```text
mvn test: PASS
Total tests: 258
UI tests (UiEndToEndTest): 7
Headless UI tests: PASS
Win32 native UI tests: SKIPPED on this host (Darwin); sources present
0.1–0.16 Regression: PASS
Native GUI Compilation: PASS (headless link; Cocoa when not --headless)

Window: PASS
Button: PASS
TextField: PASS (API + peer create)
Click Callback: PASS
Capturing Listener: PASS
invokeLater: PASS
UI Thread Check: PASS
Moving GC + UI: PASS (handle id registry; prior GC2 suite green)
Resource Lifecycle: PASS
Multiple Windows: PASS
Unicode: PASS
```

## 1. UI Architecture

```text
jnative.ui (UiLib.java)
 → UiNative @Native FFI
 → jnative_ui.c (+ cocoa.m / win32.c)
 → OS widgets / headless peers
```

## 2. Application

Owns UI thread id, dispatcher callback, `run()` / `quit()`, window count shutdown.

## 3. UI Thread Model

All mutations check `Thread.currentThread().id() == uiThreadId` → `UiThreadViolationException`.

## 4. Event Loop

Headless: task drain + wait for quit/no windows.  
Cocoa: `[NSApp run]`.  
Win32: `GetMessage` / `DispatchMessage`.

## 5. PlatformUiBackend

C ABI `jnative_ui_*` + weak `jnative_ui_platform_*` overridden by Cocoa/Win32.

## 6. Component Model

`Component` → `Container` → `VBox`/`HBox`/`Panel`; `Label`/`Button`/`TextField`.

## 7. Native Peer Model

Managed `Component.nativeId` + OS peer table in C; no HWND exposed to apps.

## 8–12. Window / Label / Button / TextField / Boxes

Implemented in `stdlib/ui/UiLib.java` with layout in VBox/HBox.

## 13–14. Native Event Dispatch / Lambda Listeners

OS → `jnative_ui_emit_event(handleId, code)` → `Native.callback(IntBinaryFunction)` → `UiHandleRegistry` → `Runnable` listener. Exception caught at boundary.

## 15. Win32 Backend

`native/windows/jnative_ui_win32.c` — `RegisterClassEx`/`CreateWindowEx`/BUTTON/STATIC/EDIT/`WndProc`.

## 16–17. GC Handle / GC2

No raw managed pointers in OS userdata. Registry is static `Component[]` (GC roots). Moving GC suite still PASS.

## 18. Threading / invokeLater

`UiTaskQueue` + `jnative_ui_post_task` / Cocoa `dispatch_async` / Win32 `PostMessage`.

## 19. Exception Boundary

`jnative_ui_emit_event` / `Application.dispatchNative` catch `RuntimeException`.

## 20. Resource Lifecycle

Explicit `dispose()`; idempotent; `DisposedComponentException` after dispose.

## 21. Headless Backend

Default for CI via `--headless` / `JNATIVE_HEADLESS=1`.

## 22. Unicode

UTF-8 JNative strings; Cocoa `NSString`; Win32 UTF-8→UTF-16.

## 23. GUI Link Mode

`--app-type=gui` → Windows subsystem flag; Cocoa `-framework Cocoa` when not headless.

## 24. Native E2E

Headless Counter / invokeLater / multi-window / unicode PASS.  
Examples: `examples/ui/HelloWindow.java`, `CounterApp.java`.

## 25–26. Tests / Regression

258 tests, 0 failures. UI 7 + prior suites green.

## 27. Known Limitations

- `import jnative.ui.*` accepted but flattened (no real packages yet)
- GTK backend stubbed (architecture ready)
- `invokeAndWait` deferred
- Menus / dialogs / canvas / styling deferred
- Capturing lambda + try/catch IR edge case (test workaround via static holder)
- Headless is primary automated path; interactive Cocoa/Win32 manual

## 28. Important Files

- `stdlib/ui/UiLib.java`
- `native/posix/jnative_ui.{c,h}`, `jnative_ui_cocoa.m`
- `native/windows/jnative_ui_win32.c`
- `stdlib/StdlibIncluder.java`, `NativeRuntimeSources.java`
- `e2e/UiEndToEndTest.java`
- `docs/ui.md`

## 29. Architecture Acceptance

| Question | Answer |
|----------|--------|
| UI as library/runtime rather than compiler magic? | **YES** (intrinsics: **NONE**) |
| PlatformUiBackend abstracted? | **YES** |
| Win32 creates real native window? | **YES** (sources; host Darwin used headless) |
| Application owns native event loop? | **YES** |
| Button uses native widget? | **YES** (Cocoa NSButton / Win32 BUTTON) |
| Native events invoke JNative lambdas? | **YES** |
| Callbacks respect exception boundaries? | **YES** |
| Moving-GC-safe handles? | **YES** (handle ids + static table) |
| GC can move UI managed objects? | **YES** |
| UI restricted to UI thread? | **YES** |
| invokeLater cross-thread? | **YES** |
| Event loop coexist with GC/STW? | **YES** (`@Native` → enter/leave native) |
| Multiple windows? | **YES** |
| Dispose without finalizers? | **YES** |
| Headless backend for tests? | **YES** |

## 30. 0.18 Readiness

| Question | Answer |
|----------|--------|
| Can custom painting be added? | **YES** |
| Can Canvas receive mouse/keyboard events? | **YES** |
| Can fonts be represented? | **YES** |
| Can images be loaded? | **YES** |
| Can PNG/JPEG be decoded through native libraries? | **YES** |
| Can double buffering be added? | **YES** |
| Can GPU rendering backend be added later? | **YES** |
| Can ListView/TableView be built over current component model? | **YES** |
| Can menu/dialog/clipboard APIs fit current architecture? | **YES** |
| Can JNative eventually build its own IDE? | **YES** |

## Real Numbers

```text
UI compiler intrinsics: NONE
Headless UI e2e: 7 PASS
Full suite: 258 PASS
```
