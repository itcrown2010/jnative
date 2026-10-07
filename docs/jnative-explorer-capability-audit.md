# Explorer Capability Audit

**Date:** 2026-10-07  
**Scope:** `examples/explorer` + `jnative.ui` / `jnative.io` / native Cocoa·Win32  
**Rule:** class existence alone ≠ PASS.

## Entry

| Item | Value |
|---|---|
| Entry | `examples/explorer/JNativeExplorer.java` → `JNativeExplorer.main` |
| Shape | Single ~1345 LOC file (20 nested classes) |
| Runtime | Native GUI binary (`--app-type gui`); no JVM |

---

## Capability Matrix (pre-refactor baseline)

| # | Capability | Status | Evidence |
|---|---|---|---|
| 1 | Explorer entry | **PASS** | `JNativeExplorer.main` |
| 2 | JNative UI components used | **PASS** | Window, VBox/HBox, Button, TextField, Label, TreeView, TableView, SplitPane, MenuBar, Dialogs |
| 3 | True Native UI peers | **PASS** (macOS) | Cocoa NSWindow / NSTableView / NSOutlineView / NSMenu |
| 4 | Temporary draw / fake widgets | **PARTIAL** | Toolbar = HBox+Buttons; address = bare TextField; `Dialogs.prompt` fake for rename |
| 5 | TreeView real component | **PASS** | UiLib + NSOutlineView / Win32 TreeView |
| 6 | TableView real component | **PASS** | UiLib + NSTableView; **Inset style → card look**; not true virtualization |
| 7 | ScrollView | **PARTIAL** | Java class exists; peer is plain view; Table/Tree embed NSScrollView |
| 8 | ContextMenu | **PARTIAL** | Class exists; `setContextMenu` stores field only — **no show / right-click** |
| 9 | MenuBar | **PARTIAL** | macOS NSMenu OK; Win32 stub |
| 10 | FileDialog | **PASS** | NSOpenPanel / NSSavePanel; Explorer barely uses |
| 11 | Drag & Drop | **MISSING** | No DnD API (only mouse drag events) |
| 12 | Clipboard | **PARTIAL** | EditorLib text clipboard; Explorer uses **in-app** `ClipboardFiles` only |
| 13 | FileSystem API | **PASS** | `Files.listInfo/copy/move/...`, `SystemPaths`, drives |
| 14 | FileWatcher | **PASS** | `WatchService` + Explorer poll coalesce |
| 15 | Native Icon API | **MISSING** | Cocoa SF Symbol heuristic by Kind string; no NSWorkspace/SHGetFileInfo |
| 16 | Async / ThreadPool | **PARTIAL** | Ad-hoc `Thread` workers; ThreadPoolExecutor exists unused here |
| 17 | UI Thread scheduling | **PASS** | `Application.invokeLater` / `checkUiThread` |
| 18 | Keyboard / Mouse events | **PASS** | KeyEvent/MouseEvent + window handlers |
| 19 | SelectionModel | **PARTIAL** | Single-select wired; `setMultiple` not native |
| 20 | DPI / scaling | **MISSING** | No scaleFactor API |

---

## Milestone 1 target (Phase A+B+C)

Ship visual/architecture baseline:

- Toolbar (icon-first, no Go/Search buttons)
- Platform sidebar (Favorites / Locations — not “This PC” on macOS)
- BreadcrumbBar (+ Cmd/Ctrl+L address edit)
- Plain TableView (not card inset)
- Sort + selection
- Native file icons (NSWorkspace on macOS)
- Status bar density
- Controllers / Command skeleton

**Out of scope for M1:** full DnD, system file clipboard, inline rename, 100k virtualization, ContextMenu popup (platform work tracked).

---

## Owning layers for gaps

| Gap | Owning layer |
|---|---|
| Toolbar / BreadcrumbBar / Theme | `jnative.ui` |
| Table plain style + row icon path | `jnative.ui` + Cocoa/Win32 |
| FileIconService | `jnative.ui`/`jnative.io` + platform |
| ContextMenu show | `jnative.ui` + platform |
| Multi-select | `jnative.ui` + platform |
| DnD / file Clipboard | `jnative.ui.dnd` / clipboard |
| DPI | `jnative.ui` + platform |
| Explorer controllers | `examples/explorer` only |
