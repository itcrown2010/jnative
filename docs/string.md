# String

## Public contract (current)

| API | Semantics |
|-----|-----------|
| `length()` | **UTF-16 code unit** count (Java-compatible) |
| `charAt(i)` / `substring` / `indexOf` | UTF-16 code unit indices |
| `utf8Length()` | UTF-8 **byte** length of the content (no trailing NUL) |
| `getBytes()` | UTF-8 encode → `byte[]` (lone surrogates → U+FFFD) |
| `String.fromBytes(byte[])` | **Strict** UTF-8 decode (invalid → `IllegalArgumentException`) |
| `equals` / `hashCode` | UTF-16 content / Java `31*h+char` over code units |
| `toLowerCase` / `toUpperCase` / `equalsIgnoreCase` | **ASCII only** (not full Unicode case folding) |

Example: `"中文😀"` → `length()==4`, `utf8Length()==10`.

Protocol sizes (HTTP Content-Length, TCP/File writes, PG buffers) must use
`utf8Length()` or `getBytes().length`, **never** `length()` alone.

## Internal representation

```text
{ ClassMetadata* @0, i32 utf16Len @8, uint16_t data[utf16Len] @16 }
```

Implemented in `native/posix/jnative_string.c`. Immutable content; GC owns the object.

## Encoding rules

| Case | Rule |
|------|------|
| Invalid UTF-8 in `fromBytes` / literals from wire | Reject (`IllegalArgumentException`) |
| Overlong / surrogate code points in UTF-8 | Reject |
| Lone surrogates in String (e.g. mid-pair `substring`) | Allowed in UTF-16 storage |
| Encode lone surrogate to UTF-8 (`getBytes` / write) | Replace with U+FFFD |
| Embedded U+0000 in String | Preserved; `getBytes` includes `0x00` |
| `string → CString` (FFI) | Rejects embedded U+0000 |

## WASM / browser

Browser string layout remains UTF-8 byte buffers for JS `TextDecoder` interop.
`length`/`charAt` on wasm32-browser are **not** yet on the UTF-16 contract — use the
native LLVM backend for Java-compatible indexing. Migration of WAT builtins is follow-up.

## Migration from prior UTF-8-`length` era

- Replace `s.length()` used as byte count with `s.utf8Length()` or `s.getBytes().length`.
- `charAt` / `substring` on non-ASCII now follow UTF-16 (was byte index).
- `hashCode` now matches Java UTF-16 hashing for BMP + supplementary pairs.
