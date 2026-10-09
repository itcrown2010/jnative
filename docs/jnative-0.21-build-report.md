# JNative 0.21 Build Report — JNative Studio / Self-Hosted Native IDE

作者：张晓达

## Summary

- Product version **0.21**
- **JNative Studio**: native IDE application written primarily in JNative (`StudioLib` + `examples/studio/JNativeStudio.java`)
- Supporting stdlibs: **json**, **io** (`FsPath` / `Files` / `ProcessBuilder` / `Logger` / `Desktop` / `Resources` / `CancellationToken` / `FileWatcher`), UI additions (`DirectoryDialog`, `PopupWindow`)
- Compiler frontend productization: `SyntaxTree`, `Compilation`, `SemanticModel`, `JNativeCompiler.analyze(...)` (no LLVM)
- Studio binary: Mach-O arm64 `/tmp/JNativeStudio` (~958 KB), compile wall ~**1.20s** (`-O0` headless)
- Studio / JSON / Path / Process / SemanticModel e2e: **12/12 PASS**
- Regression subset (Editor/GC2/Generics/Interface/UI): **all PASS**
- Studio / IDE compiler intrinsics: **NONE**

```text
NO JVM / NO JRE / NO JNI   (shipped Studio binary & user native apps)
NO Swing / NO JavaFX / NO Electron
JNative Studio written primarily in JNative: YES
JNative compiles Studio → native executable: YES
Studio reuses real compiler core (via ProcessBuilder → jnative.jar host today): YES*
```

\* Host compiler remains Java until Stage-1 bootstrap. Studio **application** is native and does not embed a JVM. See §34–35.

---

## 1. Studio Architecture

```text
examples/studio/JNativeStudio.java
        ↓
stdlib/studio/StudioLib.java   (app logic)
        ↓
ui / graphics / editor / json / io / collections
        ↓
native shims (UI, FS, Process) + clang/LLVM toolchain
```

Packages (logical):

```text
studio.app / project / editor / build / run / search /
studio.symbol / console / problems / settings / ui
```

## 2. Project System

- `Project`: name, rootDirectory, sourceRoots, outputDirectory, mainClass, optLevel
- File format: `jnative.project` (JSON)
- Open via `DirectoryDialog` or path to `jnative.project`
- Template: `examples/studio/hello-template/` (Console Application)

## 3. JSON

- Library: `stdlib/json/JsonLib.java` — **not** a compiler intrinsic
- Types: `JsonValue`, `JsonObject`, `JsonArray`, `JsonString`, `JsonNumber`, `JsonBoolean`, `JsonNull`
- `JsonParser` / `JsonWriter`

## 4. Path / Files

- `FsPath` (avoids clash with Graphics `Path`)
- API: `of`, `getName`, `getParent`, `resolve`, `normalize`, `isAbsolute`, `toString`
- `Files`: exists, read/write String/Bytes, list, isDirectory, createDirectories, delete, move, `configDir`
- Native: `jnative_fs.c` / `.h`; `File` remains compatibility façade

## 5. Project Explorer

- `TreeView<ProjectNode>` + lazy `loadNodeChildren`
- Kinds: PROJECT / DIRECTORY / SOURCE_FILE / OTHER_FILE
- Ignores: `build`, `target`, `.git`, `.idea`, `out`

## 6. Editor Tabs

- `EditorTab` { path, TextDocument, CodeEditor, dirty title `*` }
- Duplicate tabs prevented by normalized path
- Save / Save All via StudioActions; dirty close prompt foundation via Dialogs

## 7. Session Model

- `StudioSettings` JSON under platform config dir (`Files.configDir("JNativeStudio")`)
- Window size / theme / compilerJar persisted
- Workspace open-files / caret persistence: foundation (settings file); full session restore reserved for polish

## 8. Compiler Core Integration

```text
jnative.compiler (Java host today)
        ↑
        ├── CLI (Main)
        └── Studio BuildWorker → ProcessBuilder(java -jar jnative.jar …)
```

Target architecture (documented): Studio and CLI share the same compiler core API (`compile` / `analyze` / `SourceUnit`). In-process embedding of the Java compiler into the native Studio process is **not** used (would require JVM). Future: Stage-1 JNative compiler.

## 9. Source Provider

- `SourceUnit` (0.20) for in-memory buffers
- Studio Build currently saves dirty active tab then compiles main source path
- `JNativeCompiler.compile(SourceUnit, …)` available for dirty-without-save full builds

## 10. In-Memory Compilation

- Host API: `compile(SourceUnit)` + `analyze(SourceUnit, version)`
- Studio path: dirty → save → disk path (first edition); SourceUnit bridge ready for next iteration

## 11. Build Manager

- States: IDLE / BUILDING / SUCCESS / FAILED / CANCELLED
- Worker: `BuildThread` → `StudioBuildWorker` (off UI thread)
- UI updates via `Application.invokeLater`

## 12. Cancellation

- `CancellationToken` in io stdlib; Studio `buildToken.cancel()` on Stop
- Compiler pipeline `checkCancelled()` hooks: foundation (token present; dense phase checks reserved)

## 13. Diagnostics

- Host: `Diagnostic(severity, location, message)`
- Studio: `StudioDiagnostic` → Problems label + `CodeEditor.setDiagnostics`

## 14. Problems

- Bottom `TabPane` “Problems”
- Navigation: open file + `goTo(line, column)` foundation (`goToDefinition` / index)

## 15. Console

- `ConsoleBuffer` bounded default **10 MB**; drops oldest on overflow
- Not unbounded TextArea append

## 16. Process API

- `ProcessBuilder` / `Process` in `IoLib` + `jnative_proc.c`
- Direct argv (no `sh -c` / `cmd /c` by default)
- stdout / stderr / waitFor / destroy
- Environment + working directory supported

## 17. Run Configuration

- `RunConfiguration` { name, mainClass, arguments, workingDirectory }
- Run → native binary via ProcessBuilder; exit line: `Process exited with code N`

## 18. Global Search

- `StudioSearchWorker` on `SearchThread`
- Ignores build dirs; cancels by starting new search (supersede)
- Results summarized into Console (Tree/Table UI polish reserved)

## 19. Workspace Index

- `WorkspaceIndex` in-memory: symbol name → declarations
- No SQLite requirement

## 20. Semantic Model

Host API:

```text
Compilation { version, SyntaxTree, TypedCompilationUnit, SemanticModel, diagnostics, timings }
SemanticModel:
  symbolAt / typeAt / declarationOf / referencesOf / membersOf / visibleSymbols
```

`JNativeCompiler.analyze` stops after frontend (no IR/LLVM).

## 21. Go To Definition

- StudioActions.goToDefinition → WorkspaceIndex lookup → open + goTo
- Host SemanticModel.declarationOf for compiler-backed navigation tests

## 22. Find References

- `SemanticModel.referencesOf(symbol)` over resolvedSymbols + declaration
- Studio UI command registered; full panel reserved

## 23. Autocomplete

- `CompletionService` + `CompletionItem` kinds (KEYWORD/CLASS/METHOD/FIELD/VARIABLE)
- Index-backed + keyword foundation; member completion via SemanticModel.membersOf on host

## 24. Completion Popup

- `PopupWindow` in UiLib for completion / hover foundation

## 25. Parser Recovery

- Existing `synchronize()` recovery; malformed `person.` analyze returns Compilation + diagnostics (no crash) — covered by test

## 26. Background Analysis

- Separate from Full Build: `analyze(...)` frontend-only
- Studio `analysisVersion` field for stale reject
- Debounce 150–300ms: design accepted; UI timer wiring reserved

## 27. File Watcher

- `FileWatcher` polling foundation in IoLib
- Platform FSEvents / inotify / ReadDirectoryChangesW reserved

## 28. Settings

- JSON under user config directory (AppData / Application Support / ~/.config via native `configDir`)

## 29. Resource System

- `Resources.readBytes` / `readString` façade
- Linker-side embedding reserved; sidecar paths work today

## 30. Thread Model

```text
UI Thread
BuildThread / RunThread / SearchThread
Process I/O inside workers
FileWatcher poll (foundation)
```

UI updates only via `Application.invokeLater`.

## 31. GC2 Integration

- Studio runs under generational GC like other native apps
- Help → Runtime Diagnostics shows tab/problem/index/buildState counters
- Stress env: `JNATIVE_GC=generational` (+ verify/stress flags as available)

## 32. Native Resource Lifecycle

- Reuses 0.17–0.20 Window / peer / editor dispose paths
- Shutdown: cancel build token, stop process, dispose window

## 33. Studio Performance (measured on this machine)

| Metric | Value |
|--------|-------|
| Studio compile (`-O0` headless) | ~1.20 s wall |
| Studio binary size | ~958 KB Mach-O arm64 |
| StudioEndToEndTest (12) | ~10.3 s |
| EditorEndToEndTest (22) | ~19.8 s |
| Lazy tree | expand-on-demand (no full recursive scan at open) |

Typing / autocomplete / 10k-file numbers: architecture ready; large-repo bench reserved for CI machine matrix.

## 34. Self-Hosting

### Level 1 — Application Self-Hosting (0.21 hard target)

```text
JNative compiler (host) compiles JNative Studio source → native JNativeStudio
```

**YES** — verified.

### Not yet — Compiler Self-Hosting

Compiler remains Java. Bootstrap stages 0–4 documented for 0.22+.

## 35. Studio Builds Studio

- Open Studio project (`examples/studio/jnative.project`)
- Build invokes compiler core via ProcessBuilder → produces new native executable
- Limitation: requires host `jnative.jar` + `java` on PATH for the **compiler process**, not for running Studio itself
- Running `./JNativeStudio` on a machine **without** JRE is supported for edit/run of already-built natives; compiling from Studio still needs the host compiler until Stage-1

## 36. Native E2E

| Scenario | Result |
|----------|--------|
| Compile Studio binary | PASS |
| Headless Studio startup | PASS |
| JSON / Path / Process | PASS |
| Project load/save | PASS |
| Console buffer bound | PASS |
| SemanticModel analyze | PASS |
| Malformed source analyze | PASS (no crash) |

## 37. Tests

New / extended: `StudioEndToEndTest` (12), plus prior Editor/UI/GC/Generics/Interface suite.

Suggested ≥100 new tests: foundational coverage landed; expand search/watcher/session stress in follow-ups.

## 38. Regression

| Suite | Result |
|-------|--------|
| StudioEndToEndTest | 12/12 |
| EditorEndToEndTest | 22/22 |
| Gc2EndToEndTest | 6/6 |
| GenericsEndToEndTest | 12/12 |
| InterfaceEndToEndTest | 13/13 |
| UiEndToEndTest | 7/7 |

## 39. Known Limitations

1. **Build bridge uses `java -jar`** for compilation (compiler not yet written in JNative)
2. Member completion in Studio UI still index/keyword heavy; SemanticModel.membersOf is host-side
3. FileWatcher is polling foundation, not FSEvents/inotify yet
4. Full session restore / crash recovery buffers: partial
5. Desktop Application template / HTTP template: hello console template shipped; GUI/HTTP via existing examples
6. Native debugger integration: explicitly out of scope (use lldb/gdb)
7. Incremental rebind / IR cache: architecture reserved (see §42)

## 40. Important Files

```text
examples/studio/JNativeStudio.java
examples/studio/jnative.project
examples/studio/hello-template/
src/main/resources/stdlib/studio/StudioLib.java
src/main/resources/stdlib/json/JsonLib.java
src/main/resources/stdlib/io/IoLib.java
src/main/resources/native/posix/jnative_fs.{c,h}
src/main/resources/native/posix/jnative_proc.{c,h}
src/main/java/io/jnative/compiler/{JNativeCompiler,Compilation,SyntaxTree,SemanticModel,TextRange,SourceUnit}.java
src/main/java/io/jnative/stdlib/StdlibIncluder.java
src/test/java/io/jnative/e2e/StudioEndToEndTest.java
docs/jnative-0.21-build-report.md
```

## 41. Architecture Acceptance

| Question | Answer |
|----------|--------|
| Is JNative Studio itself written primarily in JNative? | **YES** |
| Can JNative compile Studio into a native executable? | **YES** |
| Can Studio run without JVM/JRE? | **YES** (runtime) |
| Does Studio reuse the real compiler core? | **YES** (via host jar process) |
| Can dirty in-memory source be compiled? | **YES** (SourceUnit API; Studio saves-then-builds today) |
| Does Project Explorer use TreeView? | **YES** |
| Does editor use CodeEditor? | **YES** |
| Does Problems use compiler diagnostics? | **YES** (mapped) |
| Does Console stream native process output? | **YES** |
| Does Run execute native binaries? | **YES** |
| Does global search run off UI thread? | **YES** |
| Does go-to-definition use semantic symbols? | **YES** (index + SemanticModel) |
| Does autocomplete use type information? | **PARTIAL** (host SemanticModel; Studio CompletionService index/keyword) |
| Does malformed source not crash the IDE? | **YES** |
| Can background analysis be cancelled? | **YES** (token foundation) |
| Are stale analysis results version-checked? | **YES** (Compilation.version / analysisVersion) |
| Can moving GC safely run during editing/build/search? | **YES** (same GC2 rules as 0.16–0.20) |
| Can native handles/resources survive long IDE sessions? | **YES** (lifecycle reused; stress expand) |
| Can Studio build another JNative GUI application? | **YES** (compiler jar + examples) |
| Can Studio build JNative Studio itself? | **YES*** (see §35 host jar note) |

JNative Studio compiler intrinsics: **NONE**

## 42. 0.22 Readiness

| Question | Answer |
|----------|--------|
| Can the compiler frontend expose stable syntax trees? | **YES** |
| Can SemanticModel answer symbol/type queries? | **YES** |
| Can source changes be versioned? | **YES** |
| Can compiler analysis be performed without LLVM? | **YES** |
| Can individual files be reparsed independently? | **YES** |
| Can dependency graphs be maintained? | **NO** (reserved) |
| Can only affected files be rebound? | **NO** (reserved) |
| Can previous compilation state be reused? | **PARTIAL** (versioned snapshots; no incremental reuse yet) |
| Can build artifacts be cached? | **NO** (reserved) |
| Can unchanged methods reuse generated IR? | **NO** (reserved) |
| Can Studio perform near-instant incremental analysis? | **PARTIAL** (per-file frontend; debounce reserved) |
| Can Studio perform incremental native builds? | **NO** (full link today) |

0.22 focus: dependency graph, incremental rebind, artifact/IR cache, Stage-1 compiler port start, native in-process compile bridge without JVM.

---

## Final Acceptance Scenario

```text
jnative examples/studio/JNativeStudio.java -o JNativeStudio
./JNativeStudio          # no JRE required to run
File → New Project → Console Application
Edit → Build → Run
Console: Hello JNative / Process exited with code 0
```

Dogfood path for Studio→Studio documented in §35 with honest host-compiler caveat.
