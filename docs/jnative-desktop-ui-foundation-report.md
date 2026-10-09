# JNative Desktop UI Foundation Report

作者：张晓达

**Date:** 2026-10-07  
**Validator apps:** `examples/explorer` · `examples/ui-workbench` · `examples/ui/CounterApp`  
**Compile gate (this pass):** CounterApp / Explorer / UiWorkbench — **PASS** (macOS arm64 host compile)

Levels: **L0** Missing · **L1** Skeleton · **L2** Working dogfood · **L3** Tested · **L4** Hardened

---

## Maturity matrix

| Area | Level | Notes |
|---|---|---|
| Component Model | **L2** | Component / Control / Container；bounds/visible/enabled；dispose；无完整 Scene 抽象 |
| Layout | **L2** | VBox/HBox/GridPane/SplitPane；无完整 constraint solver；无 dirty-subtree 证明 |
| DPI | **L1–L2** | `Dpi.scalePercentForId`；Cocoa Retina；布局未全面 logical-px 策略 |
| Rendering | **L1** | 立即 native 更新为主；无正式 dirty paint coalesce 管线 |
| Events | **L2** | UiEvent + Mouse/Key/Focus/Window/Action/Selection；无完整 capture/bubble |
| Focus | **L1** | `FocusManager.requestFocus`；无 Tab traversal order |
| Shortcut | **L2** | `KeyCombination.withPrimary` + `ShortcutManager.dispatch` |
| Command | **L2** | `Command` / `CommandRegistry` / bindButton / setExecute；Explorer 未全面迁移 |
| Selection | **L1–L2** | `SelectionModel` 单选；多选未进 native |
| VirtualList | **L1** | `VirtualListMetrics` 视口数学；**未**接 TableView cell pool |
| TableView | **L2–L3 (mac)** | Cocoa NSTableView；全量 model sync；非百万行虚拟化 |
| TreeView | **L2–L3 (mac)** | Cocoa NSOutlineView；lazy expand 部分在应用层 |
| SplitPane | **L2** | managed divider；非 NSSplitView |
| Toolbar | **L2** | HBox + chromeStyle；无 overflow |
| Breadcrumb | **L2** | segments + edit mode；Explorer 使用 |
| SearchField | **L2** | TextField + clear；`setOnQueryChanged`；placeholder 弱 |
| Menu | **L2–L3 (mac)** | 系统 MenuBar |
| ContextMenu | **L1** | `showAt`；无自动右键 |
| Dialog | **L2** | Dialogs / FileDialog / DirectoryDialog（mac） |
| Clipboard | **L1** | 文本级；files/MIME 未完成 |
| DragDrop | **L0–L1** | mouse drag 事件；无 Dragboard |
| Icons | **L1** | SystemIcons 常量；Cocoa 表格 NSWorkspace |
| Theme | **L1–L2** | tokens + spacing；`ThemeMode` SYSTEM/LIGHT/DARK；不推 peer 着色 |
| DarkMode | **L1–L2** | `systemDark` + appearanceMode；自动 UI 刷新未全链路 |
| Accessibility | **L0–L1** | 依赖系统控件默认；无 AccessibleRole API |
| IME | **L1–L2** | 依赖 NSTextField/NSTextView；无专项测试门 |
| Resource Ownership | **L1–L2** | dispose/registry；无 UI Resource Tracker |
| Performance | **L1** | 无 FPS/handle 自动门禁 |
| macOS | **L2–L3** | 主力验收平台 |
| Windows | **L1** | 骨架；未作本轮验收 |
| Linux | **L0** | 标 PARTIAL/Missing；不假装 PASS |

---

## Delivered this pass（Foundation Hardening）

1. **Geometry:** `Point` / `Size` / `Rect` / `Insets`（logical px 约定）  
2. **DPI:** `Dpi.scalePercentForId`  
3. **UiThread:** `assertCurrent` / `isCurrent` / `runLater`  
4. **Theme:** spacing tokens + appearance mode + `isDarkEffective`  
5. **Command framework:** `Command` + `CommandRegistry` + `ShortcutManager` + PRIMARY shortcut  
6. **SearchField / Toolbar / BreadcrumbBar**（可复用 chrome）  
7. **FocusManager / VirtualListMetrics / SystemIcons**（骨架）  
8. **Studio 冲突修复:** `StudioCommandRegistry` + `StdlibIncluder` 去掉误触发  
9. **Reuse smoke:** `examples/ui-workbench` 编译通过，使用同一套 Toolbar/Split/Tree/Table/Command/Search/Breadcrumb  

---

## Studio reuse test

| Question | Result |
|---|---|
| Workbench 能否不依赖 Explorer 类型？ | **YES** |
| Command / SearchField / Breadcrumb 是否在 UiLib？ | **YES** |
| Table 百万行是否已虚拟化可复用？ | **NO**（仅 metrics 演示） |
| Theme/DPI 是否足够 Studio 直接用？ | **PARTIAL** |

若问题 J（Studio 复用）要求 VirtualList/Command 全面化：当前 **PARTIAL** — chrome/command 可复用，数据视图性能层未完成。

---

## Acceptance questions（诚实回答）

| # | Question | Answer |
|---|---|---|
| A | 100k 文件流畅？ | **未证明**（无 stress gate） |
| B | UI thread 不被 FS 阻塞？ | Explorer 异步扫描路径存在；**未形式化证明** |
| C | Command 共享逻辑？ | Workbench **YES**；Explorer 工具栏/快捷键 **YES**（菜单仍 PARTIAL） |
| D | TableView 真虚拟化？ | **NO** |
| E | Native handle 泄漏检测？ | **NO** tracker |
| F | Retina？ | Cocoa scale **YES**；布局策略 **PARTIAL** |
| G | Dark Mode 自动？ | 可读系统；全 UI 自动换肤 **PARTIAL** |
| H | 键盘完整操作？ | Shortcut 骨架；Tab 遍历 **NO** |
| I | 外部文件变化？ | 依赖既有 watcher 路径；本轮未加强 |
| J | Studio 复用？ | chrome/command **YES**；虚拟化/主题深度 **NO** |

---

## Definition of Done（本轮）

| Criterion | Status |
|---|---|
| Explorer looks like desktop app | **PARTIAL**（M1 视觉已改进） |
| Explorer behaves like desktop app | **PARTIAL** |
| Stress testing | **NO** |
| Reusable UI capabilities introduced | **YES**（Command/Search/Breadcrumb/Theme/Dpi/UiThread） |
| Studio can reuse | **PARTIAL**（Workbench 证明） |

**Overall foundation pass:** **M2-FOUNDATION-PARTIAL** — 架构与可复用 API 已落地；虚拟化、跨平台、资源追踪、完整事件/焦点仍待后续阶段。
