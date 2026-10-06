# JNative 0.18 Build Report — Advanced UI Controls

## Summary

- Product version **0.18**
- Advanced desktop controls as **stdlib + native peers** (no compiler UI intrinsics)
- Event model, virtualized List/Table/Tree, menus, dialogs, Grid/Split/Tab, headless harness
- Leverages 0.15–0.17: generics, collections (`ArrayList`), interfaces/SAM, GC2 handle registry

```text
Advanced UI compiler intrinsics: NONE
Control hierarchy (Control / Container): PASS
Event system (multi-listener, consume): PASS
CheckBox / RadioButton / ToggleGroup / ComboBox: PASS
TextArea / ProgressBar / ScrollView: PASS
TabPane / SplitPane / GridPane: PASS
ListView / TableView virtualization: PASS (managed row components = 0)
TreeView lazy TreeModel: PASS
MenuBar / Menu / MenuItem / FileDialog / Dialogs: PASS
Headless UiTest + UiAdvancedEndToEndTest: PASS
ParameterizedType → Component assignability: PASS
Parameterized SAM lambdas (CellRenderer/EventHandler/Function): PASS
```

## 1–2. Architecture / Control Hierarchy

Unchanged stack: Application → Component tree → peers → PlatformUiBackend (Win32 / Cocoa / Headless).

```text
Component
├── Container: Panel, VBox, HBox, ScrollView, TabPane, SplitPane, GridPane
└── Control: Label, Button, TextField, TextArea, CheckBox, RadioButton,
             ComboBox<T>, ListView<T>, TableView<T>, TreeView<T>, ProgressBar,
             Menu*, MenuItem
```

## 3–6. Event System

`UiEvent` + subclasses; `EventHandler<E>`; 0..N handlers via `ArrayList`; snapshot-size iteration; `consume()` / `isConsumed()`.

Native: `jnative_ui_emit_event_ex` + arg0..arg3 side-channel.

## 7–10. Form Controls

CheckBox / RadioButton + ToggleGroup; ComboBox&lt;T&gt; + CellRenderer; TextArea; ProgressBar 0..1.

## 11–14. Layout Containers

ScrollView, TabPane, SplitPane (divider percent via native `value_i32`), GridPane (`addAt` / `addAtSpan`).

## 15–22. List / Table / Tree

- `ListModel` / `ArrayListListModel` / virtualized native peer (`visibleRows`, no row Components)
- `TableColumn` / `ColumnRenderer`; row selection; string insertion sort view
- `TreeModel` lazy expand via managed `expandedNodes`

## 23–26. Menus & Dialogs

MenuBar / Menu / MenuItem / Separator / ContextMenu; Dialogs.info/confirm/error; FileDialog.open/save (NativePtr path + fromCString); headless programmed results.

## 27–28. Win32 / Cocoa

Win32: InitCommonControlsEx + WC_LISTVIEW / TREEVIEW / TAB / PROGRESS / COMBOBOX / multiline EDIT.  
Cocoa: NS control mappings + NSAlert / NSOpenPanel / NSSavePanel.  
API remains platform-neutral.

## 29–31. GC2 / Threads / Lifecycle

Handle registry (int id → Component); dispose unregister; invokeLater worker→UI; exceptions caught in `dispatchNative`.

## 32–36. Headless / Demos / Perf

`UiTest` / `UiTestHarness`; examples `MiniExplorer`, `DataViewer`; metrics: `liveNativeHandles`, `layoutPasses`, `managedRowComponentCount=0`.

## 37. Known Limitations

- Nested capturing lambdas still fragile (use static Holder)
- `TableColumn` + `Function` lambda field access on type vars: prefer `ColumnRenderer` impl classes
- Full Win32 IFileDialog / rich Cocoa menu wiring deferred
- GTK backend still stub
- No accessibility / Concurrent UI collections
- List.sort-style Comparator on tables deferred beyond string view sort

## 38. Important Files

| Area | Path |
|------|------|
| Stdlib | `src/main/resources/stdlib/ui/UiLib.java` |
| ABI | `native/posix/jnative_ui.h` / `jnative_ui.c` |
| Cocoa | `jnative_ui_cocoa.m` |
| Win32 | `native/windows/jnative_ui_win32.c` |
| Assignability | `ClassType.java` (ParameterizedType → class) |
| SAM generics | `SemanticAnalyzer.java` (ParameterizedType FunctionType) |
| E2E | `UiAdvancedEndToEndTest.java`, `UiEndToEndTest.java` |
| Demos | `examples/ui/MiniExplorer.java`, `DataViewer.java` |

## 39. Architecture Acceptance

```text
Does ListView<T> use generics? YES
Does TableView<T> use generics? YES
Does TableColumn<T,R> support lambda value providers? YES (SAM path; ColumnRenderer recommended)
Can primitive table values avoid mandatory boxing? YES (specialization path / int columns)
Does TreeView support model-based lazy data? YES
Are large lists virtualized? YES
Are large tables virtualized? YES
Are events platform-neutral? YES
Can native notifications invoke managed handlers safely? YES
Are managed exceptions stopped at native boundaries? YES
Are UI native handles moving-GC safe? YES
Can background threads update through invokeLater? YES
Can dialogs coexist with GC/event loop? YES
Can FileDialog integrate with File IO? YES
Can Headless backend test advanced controls? YES
Can an IDE-like application be built? YES
Were compiler intrinsics avoided? YES
```

## 40. 0.19 Readiness

```text
Can Component support custom painting? YES
Can Canvas fit existing Component model? YES
Can mouse coordinates map into Canvas? YES
Can Image be a managed wrapper around native image data? YES
Can PNG/JPEG decoding use native libraries safely? YES
Can Font be platform-independent at API level? YES
Can text measurement integrate with layout? YES
Can double buffering be added? YES
Can a software raster backend exist? YES
Can Direct2D be added on Windows? YES
Can CoreGraphics be added on macOS? YES
Can Cairo be added on Linux? YES
Can future GPU rendering fit without redesigning Component? YES
Can a syntax-highlighted code editor eventually be built? YES
```
