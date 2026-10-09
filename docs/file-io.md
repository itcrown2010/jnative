# File I/O (0.8.1)

作者：张晓达

## API

Compiler builtin `File` (future: `jnative.io.File`):

```java
File.readString(path)   // UTF-8 → GC String
File.readBytes(path)    // → managed byte[]
File.writeString(path, text)
File.writeBytes(path, data)
File.exists(path)
File.size(path)         // long (64-bit)
File.delete(path)
```

Paths are JNative `String` values. Relative paths use the process **current working directory**.

## Architecture

```text
File.*  →  semantic builtin  →  IR Call (jnative_file_*)
        →  FileRuntimeEmitter  →  libc (fopen/fread/…)  →  OS
```

Frontend / LLVM backend do **not** hardcode `fopen`. Platform details stay in the runtime layer so POSIX / Windows backends can diverge later.

`File` does **not** expose `FILE*`.

## Encoding

`readString` / `writeString` are **UTF-8 only**, matching JNative String.

## Memory / GC

| Buffer | Ownership |
|--------|-----------|
| Temporary path CString / read buffer | `malloc` → always `free` |
| Result `String` / `byte[]` | GC-managed |

`readString` copies from a temporary native buffer into a GC String (does not stage through a managed `byte[]`, to avoid GC collecting the source mid-copy).

## Exceptions

```text
Exception
└─ IOException
   └─ FileNotFoundException
```

Missing file on read/size → `FileNotFoundException` (catchable).
Other I/O failures / oversized files → `IOException`.

## argv

`main(String[] args)` receives a real managed `String[]` built from process `argc`/`argv`
(Java-style: program name excluded).

## Examples

- `examples/Cat.java` — `./jcat file.txt`
- `examples/Copy.java` — `./jcopy src dst`

## Limitations

No `java.nio`, channels, mmap, async I/O, networking, directories APIs, charset framework.
Public API is read-all / write-all; internal runtime leaves room for a future `FileHandle`.
