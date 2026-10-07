# JNative Desktop UI Architecture Audit

**Audit date:** 2026-10-07  
**Scope:** `stdlib/ui/UiLib.java` · `native/posix/jnative_ui.{h,c}` · `jnative_ui_cocoa.m` · `jnative_ui_win32.c` · `examples/explorer` · `examples/ui-workbench`  
**Evidence rule:** class existence ≠ Working；以源码实装 + 编译/dogfood 为准。

---

## 0. Executive summary

JNative Desktop UI 分层：

| Layer | Path | Role |
|---|---|---|
| Managed API | `src/main/resources/stdlib/ui/UiLib.java` | Component 树、布局、事件、Command |
| Neutral core | `native/posix/jnative_ui.c` + `.h` | handle 分配、headless、weak platform hooks |
| macOS | `jnative_ui_cocoa.m` | **主力实装**（AppKit） |
| Windows | `jnative_ui_win32.c` | CreateWindow 骨架；多项数据同步为空 |
| Linux/GTK | — | **无源文件**；`platform_family=3` → headless/weak stub |

**结论：** macOS ≈ L2–L3（Explorer / Workbench dogfood）；Windows ≈ L1；Linux Desktop UI = Missing。无 L4。

**命名冲突（已修复）：** Studio `CommandRegistry` → `StudioCommandRegistry`；`StdlibIncluder` 不再因 UI 源码中的 `CommandRegistry` 误注入 StudioLib。

---

## 1. Architecture

```text
Application / UiThread / invokeLater
        │
        ▼
Component Tree  ←→  UiHandleRegistry  ←→  UiNative
   │                                         │
   ├─ Layout (VBox/HBox/Grid/Split)          ├─ jnative_ui.c
   ├─ Controls / Composites                  └─ PlatformUiBackend
   └─ Command / Shortcut / Theme                   ├─ Cocoa (real)
                                                   ├─ Win32 (partial)
                                                   └─ GTK (absent)
```

业务代码不得直接依赖 Cocoa / Win32 / GTK。当前 Explorer / Workbench 仅使用 `jnative.ui.*`。

---

## 2. Control-by-control

图例：**Status** = real native / custom drawn / wrapper / demo-only / missing

| Control | Status | macOS | Windows | Linux |
|---|---|---|---|---|
| Window | real native | Working | Partial (`set_content` 弱) | Missing |
| Component / Container | wrapper + peer | Working | Partial | Stub |
| Layout (V/H/Grid) | custom layout | Working | Partial | Missing |
| Button / Label / TextField | real native | Working | Partial | Missing |
| ScrollView | wrapper / fake peer | Partial | Stub | Missing |
| TreeView | real native + model | Working | Stub→Partial | Missing |
| TableView | real native + model | Working（非真虚拟化） | Stub | Missing |
| Menu / MenuBar | real native | Working | Stub | Missing |
| ContextMenu | native showAt | Partial（无自动右键） | Stub | Missing |
| Dialogs / FileDialog | OS modal API | Working | Partial/Stub | Missing |
| SplitPane | custom layout | Working*（无系统拖拽条） | Stub | Missing |
| Image / Icon UI | missing in UiLib | — | — | — |
| Toolbar / Breadcrumb / SearchField | wrapper composites | Working | Partial | Missing |
| SystemIcons | string constants | Partial* | Missing | Missing |
| Command / ShortcutManager | wrapper | Working（Workbench） | Partial | — |
| Theme / Dpi / UiThread | wrapper | Partial→Working | Stub | Missing |
| VirtualListMetrics | demo helper | Partial | — | — |
| FocusManager | thin wrapper | Partial | Partial | Missing |
| SelectionModel | wrapper | Partial（单选） | Partial | — |

\* macOS 表格图标经 NSWorkspace；非跨平台 Icon API。

---

## 3. Strategy confirmation（与产品目标对齐）

| Prefer native integration | Prefer JNative-managed |
|---|---|
| Window, Menu, Dialog, Clipboard, FileDialog, Accessibility, DnD | TableView / TreeView / CodeEditor / VirtualList（一致性、虚拟化、Studio 复用） |

当前：Window/Menu/Dialogs 走 native；Table/Tree 为 native peer + managed model；VirtualList **尚未**驱动 TableView。

---

## 4. Critical gaps

1. Linux Desktop UI 缺失  
2. Win32 与 posix core 分叉，list/tree/menu/content 大量 stub  
3. Table 真虚拟化未落地（`VirtualListMetrics` ≠ 已虚拟化）  
4. ContextMenu 缺右键自动路径；Explorer 未绑定  
5. 无 Image/Icon 控件 API；Theme/DPI 未贯穿绘制  
6. Focus traversal / Tab 导航未完成  
7. DnD / 跨应用 Clipboard files 未完成  

---

## 5. Verdict

```text
JNATIVE_DESKTOP_UI = macOS_DOGFOOD_READY (L2–L3)
                    + WINDOWS_SKELETON (L1)
                    + LINUX_MISSING (L0)
```

以 macOS + headless e2e 为当前唯一可信质量门。
