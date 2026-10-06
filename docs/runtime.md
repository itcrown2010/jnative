# JNative Runtime

JNative 0.4 establishes a formal runtime boundary. The LLVM backend lowers IR
opcodes to calls into this API; it does not embed Java semantics.

## Encoding (String)

0.4 stores String payload as **UTF-8 bytes** for implementation simplicity.

- Documented here so frontend does not assume encoding.
- Runtime APIs isolate encoding (`jnative_string_*`).
- Migration path to UTF-16 code units is reserved for a later release.
- `String.length()` / `charAt` currently operate on UTF-8 byte indices for ASCII
  acceptance tests; non-ASCII semantics are not yet Java-compatible.

## Object header

```text
Object → ClassMetadata*
ClassMetadata → { vtable*, super*, instanceSize, flags, name* }
```

Virtual dispatch: load metadata from object, load vtable from metadata, index slot.

## Memory

| API | Role |
|-----|------|
| `jnative_alloc_zeroed(size)` | Process-lifetime heap (no GC yet) |
| `jnative_null_check(ptr)` | Throws `NullPointerException` (0.6) |
| `jnative_runtime_error(msg)` | Legacy abort helper (still available) |
| `jnative_exception_*` | Pending exception state / uncaught handler (0.6) |

## Arrays

| API | Role |
|-----|------|
| `jnative_array_alloc(elemSize, length, meta)` | Header + length + zeroed elements |
| `jnative_array_bounds_check(array, index)` | Bounds; error: array index out of bounds |

Layout: `{ ClassMetadata*, i32 length, padding, elements... }`

## Strings

| API | Role |
|-----|------|
| `jnative_string_from_literal(bytes, len)` | Build String object |
| `jnative_string_length` / `char_at` / `concat` / `equals` | String ops |

## Types (RTTI)

| API | Role |
|-----|------|
| `jnative_instanceof(obj, targetMeta)` | Walk superclass chain; null → false |
| `jnative_checked_cast(obj, targetMeta)` | Null OK; else instanceof or error |

## IO

`jnative_println_{i32,i64,f64,bool,char,string}`

## File I/O (0.8.1)

| API | Role |
|-----|------|
| `jnative_file_read_string` / `read_bytes` | Disk → GC String / `byte[]` |
| `jnative_file_write_string` / `write_bytes` | GC payload → disk |
| `jnative_file_exists` / `size` / `delete` | Metadata ops |
| `jnative_argv_to_string_array` | `argc`/`argv` → managed `String[]` |

Platform: libc today (`fopen`/`fread`/…). See [file-io.md](file-io.md).

## Networking (0.8.2)

| API | Role |
|-----|------|
| `jnative_tcp_connect` / `connect_timeout` | DNS + TCP connect → managed `TcpSocket` |
| `jnative_tcp_listen` / `listen_any` | bind/listen → managed `TcpServer` |
| `jnative_tcp_accept` | accept → new `TcpSocket` |
| `jnative_tcp_read*` / `write*` | stream I/O (partial write-all; EOF) |
| `jnative_tcp_close` / timeouts | idempotent close; `SO_*TIMEO` |

Platform: `jnative_plat_tcp_*` in bundled C (`native/posix` or `native/windows`).
See [networking.md](networking.md).

## HTTP (0.8.3)

No `jnative_http_*` runtime. HTTP is stdlib JNative source using `TcpSocket` + `Bytes` / `String` helpers.
See [http.md](http.md).

## String / Bytes helpers (0.8.3)

| API | Role |
|-----|------|
| `jnative_string_index_of*` / `substring*` / `starts_with` / `ends_with` | parsing |
| `jnative_string_equals_ignore_case` / `trim` / `to_lower` | headers |
| `jnative_string_get_bytes` / `from_bytes` / `value_of_i32` | wire ↔ String |
| `jnative_bytes_*` | concat / copy / slice / CRLF search |

## Module layout (compiler)

```text
backend/llvm/runtime/
  RuntimeEmitter
  MemoryRuntimeEmitter
  ArrayRuntimeEmitter
  StringRuntimeEmitter
  TypeRuntimeEmitter
  IoRuntimeEmitter
  FfiRuntimeEmitter
  FileRuntimeEmitter
  NetworkRuntimeEmitter
  SocketRuntimeEmitter
  DnsRuntimeEmitter
  BytesRuntimeEmitter
```

## GC readiness (0.5)

- All heap alloc routes through runtime helpers.
- `ClassMetadata.referenceFieldGepIndices` lists reference fields.
- Array flags distinguish reference vs primitive elements.
- No scanning yet; metadata is sufficient to add it later.
