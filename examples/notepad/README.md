# JNative Notepad

作者：张晓达

Native desktop notepad demo built with JNative UI + CodeEditor + File I/O.

## Build

```bash
# from repository root (bootstrap compiler may use JVM)
java -cp target/classes io.jnative.Main \
  examples/notepad/JNativeNotepad.java \
  -o build/notepad/JNativeNotepad \
  --app-type gui
```

## Run

```bash
./build/notepad/JNativeNotepad
```

No JRE required at runtime.

## Features

- New / Open / Save / Save As / Exit
- Undo / Redo / Cut / Copy / Paste / Select All
- Find / Find Next
- Dirty title (`*file - JNative Notepad`)
- Close protection (Save / Don't Save / Cancel)
- Status bar: line/column, UTF-8, LF

## Sample

`unicode.txt` — multilingual + emoji round-trip test file.
