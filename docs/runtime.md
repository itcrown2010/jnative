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

## Module layout (compiler)

```text
backend/llvm/runtime/
  RuntimeEmitter
  MemoryRuntimeEmitter
  ArrayRuntimeEmitter
  StringRuntimeEmitter
  TypeRuntimeEmitter
  IoRuntimeEmitter
```

## GC readiness (0.5)

- All heap alloc routes through runtime helpers.
- `ClassMetadata.referenceFieldGepIndices` lists reference fields.
- Array flags distinguish reference vs primitive elements.
- No scanning yet; metadata is sufficient to add it later.
