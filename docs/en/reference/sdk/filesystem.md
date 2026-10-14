# Filesystem API

**Status:** Experimental · **Source:** builtins in `SemanticAnalyzer.installFileBuiltins` + helpers in `stdlib/io/IoLib.java`

## Overview

`File` is a builtin class with static FFI intrinsics. Higher-level `Files` / streams wrap these.

## Types

### `File` (builtin)

Static methods only (no instances required for the common path).

## Methods (builtin `File`)

| Name | Signature | Description |
|------|-----------|-------------|
| `readString` | `static String readString(String path)` | Read entire file as UTF-8 string |
| `readBytes` | `static byte[] readBytes(String path)` | Read entire file as bytes |
| `writeString` | `static void writeString(String path, String text)` | Write/overwrite UTF-8 text |
| `writeBytes` | `static void writeBytes(String path, byte[] data)` | Write/overwrite bytes |
| `exists` | `static boolean exists(String path)` | Path exists |
| `size` | `static long size(String path)` | File size in bytes |
| `delete` | `static boolean delete(String path)` | Delete; returns success flag |

### `Files` (stdlib `IoLib`)

Wrappers around `File` using path objects — see `IoLib.java` class `Files` (`readString`, `writeString`, `readBytes`, `writeBytes`, …). Prefer reading the source for overloads.

## Examples

```java
File.writeString("note.txt", "hello");
String s = File.readString("note.txt");
boolean ok = File.delete("note.txt");
```

Runnable: `examples/file-io/`, recipe [read-a-file](../../recipes/read-a-file.md).

## Error Handling

IO failures raise IO-related exceptions (runtime). Always check `exists` when absence is expected.

## Thread Safety

Blocking I/O enters native sections for GC. Concurrent writers to the same path are not coordinated by `File` itself—use `Mutex` if needed.

## Platform Notes

Verified on macOS arm64. Windows filesystem support is **incomplete** relative to POSIX. **Not available** on `wasm32-browser`.

## Known Limitations

- No full `java.nio.file` clone  
- Directory walk APIs live in `IoLib` / `FileSystem` — verify each method before relying on it  
