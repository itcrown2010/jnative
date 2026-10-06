# JNative UI (0.18)

Native-widget desktop UI. **Not Swing compatibility.**

```text
jnative.ui (stdlib UiLib)
        ↓
  PlatformUiBackend (C / ObjC shim)
        ↓
  Win32 | Cocoa/AppKit | Headless | (GTK later)
```

## Principles

- Library/runtime code — **no UI compiler intrinsics**
- Single UI thread; `Application.invokeLater` for workers
- Native callbacks use handle ids → `UiHandleRegistry` (GC-safe)
- Exceptions never unwind through OS WndProc / AppKit callbacks
- Large lists/tables are **virtualized** (no per-row Components)

## Public types (0.18)

Foundation: `Application`, `Window`, `Component`, `Control`, `Container`, `Label`, `Button`, `TextField`, `Panel`, `VBox`, `HBox`

Advanced: `CheckBox`, `RadioButton`, `ToggleGroup`, `ComboBox`, `TextArea`, `ProgressBar`, `ScrollView`, `TabPane`, `SplitPane`, `GridPane`, `ListView`, `TableView`, `TreeView`, `MenuBar`, `Menu`, `MenuItem`, `Dialogs`, `FileDialog`

Events: `UiEvent`, `ActionEvent`, `MouseEvent`, `KeyEvent`, `FocusEvent`, `SelectionEvent`, `EventHandler`

## CLI

```text
--headless
--ui-backend=auto|headless|cocoa|win32|gtk
--app-type=console|gui
```

Env: `JNATIVE_HEADLESS=1` forces headless.

## Examples

- `examples/ui/HelloWindow.java`
- `examples/ui/CounterApp.java`
- `examples/ui/MiniExplorer.java`
- `examples/ui/DataViewer.java`

See also: `docs/jnative-0.18-build-report.md`
