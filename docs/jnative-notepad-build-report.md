# JNative Notepad Build Report

作者：张晓达

## Final Status

# A. JNATIVE_NOTEPAD_NATIVE_RUNNING

```text
Notepad compiler intrinsics:
NONE
```

---

## 1. Capability Audit

```text
Notepad Capability Audit

Application:     IMPLEMENTED
Window:          IMPLEMENTED
MenuBar:         IMPLEMENTED (Cocoa native MainMenu wired for this demo)
Menu:            IMPLEMENTED
MenuItem:        IMPLEMENTED
Panel/VBox/HBox: IMPLEMENTED
Label/Button/TextField: IMPLEMENTED
CodeEditor:      IMPLEMENTED
TextArea:        IMPLEMENTED (Notepad uses CodeEditor)
FileDialog:      IMPLEMENTED (open + save)
Clipboard:       IMPLEMENTED
Undo:            IMPLEMENTED (UndoManager / EditorCommand.UNDO)
Redo:            IMPLEMENTED
Find:            IMPLEMENTED (findNext + find bar)
Files.readString: IMPLEMENTED (via builtin File + Files façade)
Files.writeString: IMPLEMENTED
Word Wrap:       NOT IMPLEMENTED
```

---

## 2. Source Files

```text
examples/notepad/JNativeNotepad.java
examples/notepad/README.md
examples/notepad/unicode.txt
```

Supporting minimal platform fixes (Notepad blockers only):

```text
jnative_ui_cocoa.m   — MenuBar install, Save/Don't Save/Cancel, close veto
jnative_ui.c/.h      — menubar peer walk, close_window
UiLib.java           — Dialogs.askSave, Window.vetoClose / fireClose
EditorLib.java       — setLineNumbersVisible / setSyntaxHighlighting
```

## 3. UI Components Used

Window, MenuBar/Menu/MenuItem, VBox/HBox, Button, Label, TextField, FileDialog, Dialogs

## 4. Text Component Used

`CodeEditor` (line numbers off, syntax off, light theme, monospace 14)

## 5. File API Used

`FileDialog.open/save`, `TextDocument.load/saveAs` → `File.readString/writeString`

## 6. Clipboard API Used

`Clipboard` via `EditorCommand.CUT/COPY/PASTE`

## 7. Undo/Redo

`editor.executeCommand(UNDO/REDO)` → `UndoManager`

## 8. Find

Search menu + find bar (`TextField` + Find Next) → `CodeEditor.findNext` (wraps)

## 9. Unicode

`examples/notepad/unicode.txt` + File round-trip **PASS** (中文 / 日本語 / 한국어 / emoji)

## 10. Build Command

```bash
java -cp target/classes io.jnative.Main \
  examples/notepad/JNativeNotepad.java \
  -o build/notepad/JNativeNotepad \
  --app-type gui
```

## 11. Compiler Pipeline

```text
JNativeNotepad.java
→ JNative compiler (JVM bootstrap OK)
→ IR → LLVM → clang/Cocoa link
→ native Mach-O
```

## 12. Build Result

**PASS**

## 13. Executable Path

`/Users/zhangxiaoda/jnative/build/notepad/JNativeNotepad`

## 14. Executable Format

Mach-O 64-bit executable **arm64**

## 15. Executable Size

692880 bytes (~677 KB)

## 16. Runtime Dependencies

libSystem, libz, Cocoa, AppKit, Foundation, CoreFoundation, libobjc

## 17. JVM Dependency Check

**NO** java / jvm / jni in `otool -L` or process mappings

## 18. Launch Result

**PASS** — `./build/notepad/JNativeNotepad`

## 19. Native Window Result

**PASS** — title `Untitled - JNative Notepad` / `hello.txt - JNative Notepad`

## 20–28. Smoke Results

| Item | Result |
|------|--------|
| 20. New | PASS (menu) |
| 21. Open | PASS (CLI argv + File→Open menu present) |
| 22. Save | PASS (dirty `*` → save → clean title; file updated) |
| 23. Save As | IMPLEMENTED (dialog); UI automation flaky on Save panel |
| 24. Undo | PASS (menu) |
| 25. Redo | PASS (menu) |
| 26. Cut/Copy/Paste | PASS (menu + system clipboard paste into editor) |
| 27. Find | PASS (menu Find… / Find Next) |
| 28. Status Bar | PASS (Ln/Col + UTF-8 + LF labels) |
| 29. Dirty protection | PASS (askSave Save/Don't Save/Cancel; veto close) |
| 30. Unicode round trip | PASS (`ROUNDTRIP_OK`, 99 bytes incl. emoji) |

Saved content sample (`hello.txt` after Paste+Save):

```text
Saved via Notepad
第二行
```

## 31. GC Test

`JNATIVE_GC_VERIFY=1` launch **PASS** (window up, no fatal)

## 32. Resource Test

Open dialog → Cancel × 20 (scaled from 100): process remained alive, no crash

## 33. Screenshot Path

**UNAVAILABLE** — macOS Screen Recording TCC blocked `screencapture` in this agent environment

## 34. Bugs Found

1. Cocoa MenuBar was bookkeeping-only → menus not usable  
2. Confirm dialog had no Save/Don't Save/Cancel  
3. Window close could not be vetoed after OS close  
4. Empty `setText("")` left document dirty at startup  

## 35. Fixes Made

1. Native Cocoa `NSMenu` install from peer tree + ACTION emit  
2. Dialog type 3 = Save / Don't Save / Cancel  
3. `windowShouldClose` → managed close + `vetoClose` / `closeWindow`  
4. `markSaved()` after initial empty document  
5. Notepad-oriented editor flags (gutter/syntax off)

## 36. Remaining Limitations

- Word Wrap: **NOT IMPLEMENTED**
- Save As panel hard to drive via Accessibility automation
- Screenshot requires granting Screen Recording to the terminal/agent
- Font picker / print / rich text: out of scope

---

## Acceptance Path

```text
./build/notepad/JNativeNotepad
→ type / paste text
→ File → Save (or Ctrl/Cmd+S)
→ dirty cleared
→ Exit
→ ./build/notepad/JNativeNotepad hello.txt
→ content restored
```

Verified on this machine with native Mach-O, **no JVM at runtime**.
