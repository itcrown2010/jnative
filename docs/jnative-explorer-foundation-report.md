# JNative Explorer Report（Foundation Hardening pass）

作者：张晓达

**Date:** 2026-10-07  
**Binary:** `build/explorer/JNativeExplorer`（compile PASS）  
**Platform under test:** macOS arm64（compile/dogfood host）  
**Windows / Linux:** 未作为本轮验收平台（标 PARTIAL / Missing）

---

## Product role

Explorer 是 Desktop UI 的第一个大型验证项目，目标是逼出可复用的 `jnative.ui` 能力，而非一次性漂亮 Demo。

当前结构仍为单文件 `examples/explorer/JNativeExplorer.java`（未拆 controller/model/view）；UI 已使用：

- Toolbar / BreadcrumbBar / SearchField  
- SplitPane + TreeView + TableView  
- Theme spacing · MenuBar · Dialogs  
- TableIconPathProvider（Cocoa NSWorkspace icons）

已接线 Command：nav.back/forward/up/refresh、edit.copy/cut/paste/rename/delete、edit.find、nav.address（经 `ShortcutManager.dispatch`）。

尚未使用：ContextMenu、VirtualList、Dpi、FocusManager；菜单项仍有直接 Runnable。

---

## Metrics（本轮）

| Metric | Value | Evidence |
|---|---|---|
| startup time | **未测** | 需冷启动计时脚本 |
| 10k directory time | **未测** | 需生成测试目录 + 计时 |
| 100k directory time | **未测** | 同上 |
| scroll FPS | **未测** | 需 Instruments / 帧采样 |
| managed heap | **未测** | — |
| native heap | **未测** | — |
| native handles | **未测** | 无 Resource Tracker |
| icon cache size | **未测** | Cocoa 侧缓存未暴露 |
| file watcher count | **未测** | — |
| compile gate | **PASS** | `java -cp target/classes io.jnative.Main examples/explorer/JNativeExplorer.java` |

压力与句柄数据将在 VirtualList + Resource Tracker 落地后进入硬门禁。

---

## Visual / UX（相对 M1）

已改善（M1 起保留）：

1. 表格行非大块圆角灰背景（plain table style）  
2. Toolbar chrome 减轻  
3. 无独立 Go 按钮；Breadcrumb 地址栏  
4. SearchField 控件  
5. macOS 侧栏 section 化；减少 “This PC” 错位  
6. 文件类型图标（NSWorkspace）更丰富  
7. 行高 / Status / spacing 使用 Theme tokens  

仍待：

- Dark Mode 全链路自动刷新  
- Context menu 右键  
- Command 统一绑定工具栏/菜单/快捷键  
- 真虚拟化滚动  
- Preferences 持久化窗口/侧栏宽度  

---

## Known issues

1. **TableView 非虚拟化** — 大目录全量创建行仍有风险  
2. **Explorer 未用 Command** — 菜单/工具栏/快捷键逻辑仍分散  
3. **ContextMenu 未接线** — 右键依赖未完成  
4. **Search debounce** 在应用层；SearchField placeholder 未进 native  
5. **Win32 / Linux** 不能运行本 Explorer 作为验收  
6. **单文件结构** — 目标 `controller/model/view` 拆分未做  
7. **FileItem / generation token / icon lazy** — 部分存在于扫描路径，未完全规范化  

---

## Relation to UI foundation

| Capability | In UiLib? | Explorer uses? |
|---|---|---|
| BreadcrumbBar | YES | YES |
| SearchField | YES | YES |
| Toolbar | YES | YES |
| Command | YES | NO（待迁） |
| Theme / Dpi | YES | Theme YES / Dpi NO |
| VirtualList | metrics only | NO |
| ContextMenu | YES | NO |
| ui-workbench reuse | YES | N/A（独立验证） |

---

## Verdict

```text
EXPLORER_FOUNDATION_PASS = PARTIAL
  looks: PARTIAL→GOOD (macOS M1 visuals)
  behaves: PARTIAL
  stress: NOT GATED
  reusable UI pushed upstream: YES
  Studio reuse proven via workbench: PARTIAL
```

下一阶段优先：Explorer → Command 迁移、ContextMenu、VirtualList→TableView、Preferences、macOS screenshot light/dark gate、10k/100k 计时门禁。
