# JNative Studio — Feasibility Audit

**Date:** 2026-10-13 · **CLI:** 0.32 · **Host:** macOS arm64

## Summary

| Requirement | Status | Evidence | Blocker |
|---|---|---|---|
| File system read/write | Experimental / usable | `File.*` builtins; `FileIoEndToEndTest`; notes-cli | — |
| Directory listing / walk | Experimental / usable | `Files.list`, `Files.walk` in `IoLib`; `StudioEndToEndTest.pathResolveAndList` | — |
| Create directory | Experimental | `Files.createDirectories` | — |
| Process execution | Experimental / usable | `ProcessBuilder` / `Process` in `IoLib`; `StudioEndToEndTest.processEcho` | — |
| Invoke compiler toolchain | Usable | `java -jar jnative.jar build --project` (compiler **host** is JVM; app runtime is native) | Must pass absolute jar path |
| Native window / GUI | Partial / **blocked for product build** | `UiLib` Window/Button/TextArea; `examples/studio`, notepad | **Duplicate `Point`/`Size`/`Rect` in UiLib + GraphicsLib** — GUI studio fails compile |
| Text editor widget | Experimental (when UI builds) | `CodeEditor` / `TextDocument` in EditorLib | Depends on UI+graphics stack |
| Keyboard / mouse | Experimental (UI) | `KeyEvent` / `MouseEvent` in UiLib | Same UI blocker |
| Threading | Experimental | `Thread`, `Mutex`; StudioLib workers use threads | Native only |
| JSON / project config | Experimental / usable | `Json.parse`; `jnative.project` JSON; Studio e2e project load | JsonNumber gap unrelated |
| Error reporting | Partial | Compiler emits `file:line:col: error:`; Studio Core parses | Channel prefix `default:` must be stripped |
| Full IDE / LSP-in-app | Not required for MVP | VS Code extension separate | — |

## Architecture decision (Phase 5)

```text
Current (proven):   JNative Studio Core — CLI vertical slice
Next:               Native GUI shell (after Point/Size/Rect clash fixed)
Future:             Full IDE (explorer + editor + problems + terminal)
```

Do **not** fake a GUI. Existing `stdlib/studio/StudioLib.java` + `examples/studio/JNativeStudio.java` remain the GUI target once stdlib compiles.

## Layering

```text
CLI / (future) UI
      │
Core services (FileSystem, Project, Build, Diagnostics, Process)
      │
JNative SDK (File, Files, ProcessBuilder, Json)
      │
Native runtime
```

## Self-hosting levels (definition)

| Level | Meaning | Phase 5 |
|------|---------|---------|
| 1 | Studio written in JNative | **Yes** (Core CLI) |
| 2 | Can edit JNative projects | **Yes** (`read`/`write`) |
| 3 | Can build JNative projects | **Yes** (dogfood `hello-app`) |
| 4 | Can build itself | Partial (builds Core; GUI entry not buildable) |
| 5 | Develop Studio inside Studio | Future |
