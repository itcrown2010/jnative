# JNative Explorer — Milestone 1 Report (Phase A + B + C)

**Date:** 2026-10-07  
**Binary:** `build/explorer/JNativeExplorer` (Mach-O arm64)  
**Screenshot:** `build/explorer/verify-m1b.png`

## Status

```text
JNATIVE_EXPLORER_PARTIAL
```

Milestone 1 (UI architecture + visual reconstruction) is **shipped for macOS dogfood**.  
File-ops hardening, DnD, 100k virtualization, ContextMenu popup, soak → **not done** (Phase D–I).

---

## Phase A — Capability Audit

See [jnative-explorer-capability-audit.md](jnative-explorer-capability-audit.md).

---

## Phase B — Platform primitives added (generic, not Explorer-only)

| API | Layer | Notes |
|---|---|---|
| `Theme` | `jnative.ui` | Spacing + chrome constants; appearance mode placeholder |
| `Toolbar` / `Button.setChromeStyle` | `jnative.ui` + Cocoa | style 1=toolbar, 2=breadcrumb |
| `BreadcrumbBar` | `jnative.ui` | Segments + Cmd/Ctrl+L edit mode |
| `UiThread` | `jnative.ui` | assertCurrent / assertNotCurrent / runLater |
| `TableIconPathProvider` | `jnative.ui` | Trailing TSV path for native icons |
| `Component.userData` + `lastClicked` | `jnative.ui` | IR-safe crumb clicks (no capture lambdas) |
| `HostPlatform` | `jnative.io` | macOS / Windows / Linux hint |
| NSTableView **Plain** style | Cocoa | Removes inset “card rows” |
| `NSWorkspace iconForFile` | Cocoa | Real file icons when path provided |
| `jnative_ui_platform_set_control_style` | native | Toolbar/breadcrumb chrome |

**Explorer-specific compiler intrinsics:** ZERO  
**Explorer-specific LLVM hacks:** ZERO

**Test:** `UiAdvancedEndToEndTest#toolbarAndBreadcrumbBar` PASS

---

## Phase C — Explorer visual reconstruction

Layout now:

```text
Toolbar (nav + edit actions + search field)
BreadcrumbBar
SplitPane( TreeView sidebar | TableView )
StatusBar
```

| Item | Result |
|---|---|
| Modern toolbar (no Go/Search buttons) | PASS |
| Platform sidebar (Favorites / Locations on macOS) | PASS (Locations volume list still thin) |
| Breadcrumb | PASS (`/ › Users › …`) |
| Table columns Name / Date / Size / Kind | PASS |
| Plain rows (not gray cards) | PASS |
| Native icons via NSWorkspace | PASS (system folders show distinct icons) |
| Status bar density | PASS (`N items` + size) |
| Nav history enable/disable | PASS |

---

## Explorer Capability Report (post-M1)

| Capability | Status |
|---|---|
| Native Window | PASS |
| Toolbar | PASS |
| Breadcrumb | PASS |
| Sidebar | PARTIAL (TreeView + platform labels; no SidebarSection collapse widget) |
| SplitPane | PASS |
| TreeView | PASS |
| TableView | PASS |
| Virtualization | PARTIAL (unchanged) |
| Selection | PARTIAL (single) |
| ContextMenu | PARTIAL (no popup) |
| MenuBar | PASS (macOS) |
| Keyboard | PARTIAL (Cmd+L/F added; Command architecture incomplete) |
| DragDrop | MISSING |
| Clipboard (files) | PARTIAL (in-app only) |
| Filesystem | PASS |
| WatchService | PASS |
| FileIcon | PARTIAL (Cocoa NSWorkspace; no Win/Linux backend yet) |
| Trash | PASS (existing) |
| Async IO | PARTIAL |
| ThreadPool | PARTIAL |
| Search | PARTIAL (debounce 300ms) |
| DarkMode | PARTIAL (Theme.mode only) |
| DPI | MISSING |
| Accessibility | PARTIAL |

---

## Platform defects exposed

| Capability | Problem | Owning Layer | Generic Fix | Tests Added | Explorer Usage |
|---|---|---|---|---|---|
| Lambda captures | Capturing `this`/locals in UI lambdas → IR NPE | Compiler IR | Prefer static dispatch / `userData` | toolbar test avoids captures | Crumb clicks via `lastClicked` |
| Table inset style | Card-like rows | Cocoa UI | `NSTableViewStylePlain` | visual | File list |
| File icons | Kind-string SF Symbols only | Cocoa UI | `NSWorkspace iconForFile` + path TSV | — | `TableIconPathProvider` |
| Runnable implement | Cannot `implements Runnable` | Language | Use SAM lambda or interface | — | `BreadcrumbNavigate` class |

---

## Remaining blockers (next milestones)

**M2:** Command architecture, ContextMenu show, inline rename, conflict UI, system file clipboard  
**M3:** True table virtualization, async enumerate batches, search cancel, watcher coalesce polish, DnD  
**M4:** DPI, dark mode peers, a11y names, 100k + soak → only then `JNATIVE_EXPLORER_HARDENED`
