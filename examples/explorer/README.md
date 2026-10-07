# JNative Explorer

Native desktop file manager built entirely with JNative (language → compiler → IR → LLVM → native binary).

## Build

```bash
java -cp target/classes io.jnative.Main \
  examples/explorer/JNativeExplorer.java \
  -o build/explorer/JNativeExplorer --app-type gui

./build/explorer/JNativeExplorer
```

On Windows (cross-compile or native host):

```text
jnative examples/explorer/JNativeExplorer.java -o JNativeExplorer.exe --app-type gui
JNativeExplorer.exe
```

## Runtime requirements

- **NO** JVM / JRE / JNI
- **NO** Swing / JavaFX / Electron / Node.js
- Links OS UI (Cocoa on macOS, Win32 on Windows) + `jnative_fs` filesystem ABI

## Features (phase 1)

- Navigation toolbar: Back / Forward / Up / Refresh
- Address bar + search
- Quick Access + dynamic drive roots (`FileSystem.roots()`)
- Virtualized-style `TableView` listing (`Files.listInfo`)
- Open folder / open file via `Desktop.openPath` (ShellExecuteW / `open` / `xdg-open`)
- New folder, rename, copy/cut/paste (async worker thread)
- Delete → Trash / Recycle Bin (`Desktop.moveToTrash`)
- Directory watcher (`WatchService`) with coalesced refresh
- Keyboard shortcuts (Enter, Backspace, F5, Delete, Ctrl/Cmd+C/X/V/A, F2)

## Architecture

```text
Explorer UI
   ↓
jnative.io (FsPath / Files / FileInfo / WatchService / SystemPaths / Desktop)
   ↓
jnative_fs C ABI
   ↓
posix (stat/opendir/kqueue|inotify)  |  Windows (FindFirstFileW / ShellExecuteW / ReadDirectoryChangesW)
```

Explorer-specific compiler intrinsics: **NONE**.
