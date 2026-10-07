# JNative Explorer

Native desktop file manager (JNative → IR → LLVM → Mach-O / PE).  
**No** JVM / Swing / Electron at runtime.

## Build

```bash
java -cp target/classes io.jnative.Main \
  examples/explorer/JNativeExplorer.java \
  -o build/explorer/JNativeExplorer --app-type gui

./build/explorer/JNativeExplorer
```

## Milestone 1 UI

```text
Toolbar → BreadcrumbBar → Split(Sidebar Tree | Table) → Status
```

- Platform sidebar (macOS Favorites / Locations; Windows Quick Access / This PC)
- Breadcrumb segments (Cmd/Ctrl+L → address edit)
- Table: Name · Date Modified · Size · Kind + NSWorkspace icons (macOS)
- Search field with 300ms debounce (no separate Search button)

See `docs/jnative-explorer-m1-report.md` and `docs/jnative-explorer-capability-audit.md`.

## Rules

- Explorer-specific compiler intrinsics: **NONE**
- Missing UI capabilities must be fixed in `jnative.ui` / native peers, not faked in this folder
