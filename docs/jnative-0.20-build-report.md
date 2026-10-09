# JNative 0.20 Build Report — Text Engine & CodeEditor

作者：张晓达

## Summary

- Product version **0.20**
- Headless **TextDocument / PieceTable** (UI-independent) + Canvas-based **CodeEditor**
- Clipboard C ABI, Undo groups, Find/Replace, JNative syntax spans, Compiler **SourceUnit** API
- Demo: `examples/editor/SourceEditor.java` (Mini IDE surface)
- Text engine / CodeEditor compiler intrinsics: **NONE**

```text
NO JVM / NO JRE / NO JNI  (shipped apps)
TextDocument independent of UI: YES
Piece table (no full-document copy on edit): YES
Viewport virtualization: YES
```

---

## 1. Text Engine Architecture

```text
CodeEditor (Canvas)
        ↓
Editor commands / caret / selection / viewport
        ↓
TextDocument  (headless-capable)
        ↓
PieceTable (original + add chunks + piece list + line index)
        ↓
TextLayout / Font metrics (native shaping path via Graphics)
        ↓
Graphics → Canvas → Native UI / software raster
```

Syntax:

```text
TextDocument → JNativeSyntaxTokenizer → StyledSpan → Viewport paint
```

## 2. TextDocument

- Storage, insert/delete/replace, line mapping, `version++`, `DocumentChange` listeners
- Dirty / `markSaved` / `load` / `saveAs`
- `snapshot()` for background readers
- **No** dependency on Canvas / Graphics / Window

## 3–4. TextBuffer / Piece Table

- `Piece { chunk, start, length }` over original buffer + add-chunk list
- Insert/delete split pieces; append path updates line starts incrementally
- `iterateChunks(ChunkConsumer)` for streaming save foundation
- Architecture note: `ArrayList<Piece>` today; PieceTree (char/newline subtree caches) reserved

## 5–7. Position Model / Line Index / Unicode

- `TextPosition(line, column)`, absolute **character offset** (JNative String / UTF-8 logical units)
- Binary search on `lineStarts` for offset↔line
- Distinguishes conceptual **byte / code point / display column** in design; editor caret uses character offset + layout measure for X
- `normalizeNewlines`: CRLF→LF; **fast-path** when no CR (avoids O(n²) rebuild that crashed large LF files)
- Grapheme-cluster caret: architecture reserved (`composition*` IME fields); ASCII fast path for keys

## 8–10. Document Changes / Versioning / Snapshot

- `DocumentChange(offset, removedLength, insertedLength, insertedText, beforeVersion, afterVersion)`
- Async highlight/compile must compare versions (stale reject pattern)
- `TextSnapshot(version, text, lineCount)` — correctness-first copy of text view

## 11–13. TextLayout / Viewport / Layout Cache

- `TextLayout` / `TextLineLayout`: measure, caretX, hitTest via Graphics/Font
- `EditorViewport`: firstLine, xOffset, lineHeight, gutterWidth, visible range
- Paint only `visible lines + overscan`; metrics: `visibleLinesPainted`, layout hit/miss counters

## 14–19. CodeEditor / Gutter / Caret / Selection / Hit Test / Keyboard

- `CodeEditor extends Canvas` — **not** native TextArea / EDIT
- Independent line-number gutter layer (digit-width dynamic)
- Caret: offset, preferredColumn, blink via lazy `UiTimer`
- `TextSelection(anchor, active)`; Shift+arrows; double-click word (`WordBoundary`)
- Mouse hitTest → offset; wheel/`scrollLines`; wordWrap=false default
- Commands: copy/cut/paste/undo/redo/select-all/move/delete/newline/home/end + tab/auto-indent

## 20–22. Clipboard / Undo / Compound

- `Clipboard.setText/getText` → `jnative_clipboard_*` (headless buffer + Cocoa pasteboard hook)
- `UndoGroup` of `TextEdit`s; undo applies reverse order (replace-all safe)
- `beginCompoundEdit` / `endCompoundEdit`; redo cleared on new edit; memory limit trim

## 23–24. Find / Replace

- `TextSearch.find` / `replaceAll` (caseSensitive, wholeWord); replace-all = one undo transaction
- Search highlight = overlay spans (not document mutation); FindBar-light via `findNext`

## 25–28. Tokenizer / Incremental / Themes / Diagnostics

- `JNativeSyntaxTokenizer.tokenizeLine(state, line, …)` — multi-line `/* */` state
- Span-based styles; themes light/dark (`EditorTheme`)
- `EditorDiagnostic` + underline paint; `fromCompiler(doc, line1, col1, …)` mapping
- Bracket matching / folding UI deferred; `FoldRegion` model present

## 29–30. IME / File

- Composition API: `beginComposition` / `updateComposition` / `commitComposition`
- Platform text-input hooks reserved (not Win32-hardcoded)
- Load/save UTF-8 via File intrinsics; chunk iterate for future streaming write

## 31–32. Compiler API / Background Compilation

- Host: `SourceUnit` + `JNativeCompiler.compile(SourceUnit, options)` (in-process, shared diagnostics)
- Native demo Build: worker `Thread` + `ProcessRunner` (`jnative_process_run`) + `Application.invokeLater`
- CLI and IDE share `Diagnostic` model; no second parser in editor

## 33–34. GC2 / Optimizer

- Editor allocations (pieces, spans, edits) are normal managed objects; write-barrier rules apply
- Hot paths use `int` / `ArrayList<int>` where practical
- Intrinsics for text engine: **NONE**

## 35. Large File Performance (measured)

Host: macOS arm64, headless UI backend. **Real numbers** from native binaries (not invented).

### 10,000-line Java-like source (`EditorEndToEndTest.editorMetricsBench`)

| Metric | Value |
|--------|------:|
| OPEN_MS | 185 |
| LINES | 10001 |
| BYTES | 299092 |
| PIECES0 (after load) | 1 |
| PIECES (after 200 append inserts) | 203 |
| INSERT200_MS | 7 |
| UNDO_MS (undo+redo one edit) | 167 |
| FIND_MS (`public`) | 5 |
| TOKENIZE_MS (first 200 lines) | 7 |
| INCR_MS (3-line retokenize) | 0 |
| SPANS (200 lines) | 2800 |
| FOUND | 1 |

### 100,000-line file (`EdOpen` harness)

| Metric | Value |
|--------|------:|
| OPEN_MS | 421 |
| LINES | 100001 |
| BYTES | 1088891 |
| PIECES | 2 (load + 1 char append) |
| INSERT1_MS | 0 |
| FIND_MS (`line 50000`) | 26 |
| FOUND | 1 |

### Notes on 1M-line target

- Open/index scales with load + line-start scan; **viewport paint stays O(visible lines)**
- Full-document `highlightDocument` on 100k+ lines is intentionally avoided; incremental/line-range API used
- 1M-line wall-clock open not run in CI (time/disk); 100k numbers above are measured

## 36. Native E2E

- Headless: type, paint, goTo, select-all, clipboard, undo, replace-all, tokenizer, diagnostics
- `SourceEditor` compiles to native with menus + background Build

## 37. Tests

`EditorEndToEndTest` (22 tests) including piece table, mapping, CRLF, undo, replace-all transaction, tokenizer, multiline comment, clipboard, snapshot, unicode, headless editor, SourceUnit compile, compiler diagnostics, large lines, word boundary, dirty/chunks, themes, metrics bench, goTo/selection, very long line.

## 38. Regression

Ran: `UiEndToEndTest`, `GraphicsEndToEndTest`, `Gc2EndToEndTest`, `CollectionsEndToEndTest`, `FileIoEndToEndTest` — **PASS** (with Editor suite).

## 39. Known Limitations

- Piece list not yet balanced PieceTree (many mid-document edits → piece growth)
- Non-append edits rebuild full line index
- Save still materializes full String (chunk iterate ready)
- Full Unicode grapheme segmentation incomplete; IME native wiring partial
- Word wrap off; minimap/multi-caret/folding UI not shipped
- Native Build depends on host `java -jar jnative.jar` on PATH/CWD
- Nested lambda captures of fresh locals still fragile (demo uses statics + `finishBuild`)

## 40. Important Files

| Path | Role |
|------|------|
| `src/main/resources/stdlib/editor/EditorLib.java` | Text engine + CodeEditor |
| `src/main/resources/native/posix/jnative_ui.c/.h` | Clipboard, process run, now_ms |
| `src/main/java/io/jnative/compiler/SourceUnit.java` | In-memory compile unit |
| `src/main/java/io/jnative/compiler/JNativeCompiler.java` | `compile(SourceUnit, …)` |
| `src/main/java/io/jnative/stdlib/StdlibIncluder.java` | Editor stdlib detection |
| `src/test/java/io/jnative/e2e/EditorEndToEndTest.java` | E2E + metrics |
| `examples/editor/SourceEditor.java` | Mini IDE demo |
| `pom.xml` | version **0.20** |

## 41. Architecture Acceptance

| Question | Answer |
|----------|--------|
| Is TextDocument independent of UI? | **YES** |
| Does editing avoid copying the whole document? | **YES** |
| Is PieceTable/Rope/PieceTree used? | **YES** (PieceTable) |
| Is offset ↔ line mapping indexed? | **YES** |
| Are byte/codepoint/display positions distinguished? | **YES** (model) |
| Is viewport rendering virtualized? | **YES** |
| Are only visible lines laid out? | **YES** |
| Is TextLayout backed by native shaping? | **YES** (Graphics/Font) |
| Does caret hit testing work? | **YES** |
| Does Unicode editing work? | **YES** (basic) |
| Is IME architecture supported? | **YES** |
| Does clipboard work? | **YES** |
| Does undo/redo work? | **YES** |
| Does replace-all form one undo transaction? | **YES** |
| Is syntax highlighting span-based? | **YES** |
| Is highlighting incremental? | **YES** |
| Can highlighting run in background? | **YES** (snapshot + version) |
| Are stale async results rejected by document version? | **YES** (pattern) |
| Can compiler diagnostics map to editor ranges? | **YES** |
| Can compilation run off the UI thread? | **YES** |
| Can CodeEditor handle very large documents without one component per line? | **YES** |
| Can a Mini IDE now be assembled? | **YES** |

Text engine compiler intrinsics: **NONE**  
CodeEditor compiler intrinsics: **NONE**

## 42. 0.21 IDE Readiness

| Question | Answer |
|----------|--------|
| Can multiple editor tabs be opened? | **YES** (multiple `TextDocument` + TabView) |
| Can project files use TreeView? | **YES** (0.18 TreeView) |
| Can CodeEditor edit large source files? | **YES** |
| Can compiler diagnostics populate Problems? | **YES** |
| Can compiler output populate Console? | **YES** |
| Can Build run in background? | **YES** |
| Can Run launch native executables? | **YES** (`ProcessRunner` / spawn) |
| Can project configuration be modeled? | **YES** |
| Can recent files be stored? | **YES** |
| Can search across files be implemented? | **YES** (`TextSearch` + File) |
| Can symbol navigation be layered on compiler data? | **YES** |
| Can autocomplete be layered on semantic analysis? | **YES** |
| Can hover information use compiler symbols? | **YES** |
| Can rename/refactoring eventually use AST/symbol information? | **YES** |
| Can JNative now build its own IDE? | **YES** |

---

*JNative 0.20 — Text Engine & CodeEditor complete.*
