# String

## Status (0.4)

`String` is a reference/object type with ClassMetadata, not a println-only
special LLVM `i8*`.

## Layout (runtime)

```text
JNativeString
  ClassMetadata*
  i32 length          // UTF-8 byte length in 0.4
  ptr data            // UTF-8 bytes
```

**Encoding choice:** UTF-8 for 0.4 simplicity. Frontend must not bake in
encoding assumptions; all access goes through `jnative_string_*`. Future
releases may migrate payload to UTF-16 code units.

## Operations

| Feature | Status |
|---------|--------|
| Literals → String objects | Yes |
| `+` concatenation | `jnative_string_concat` |
| `length()` | Yes |
| `charAt(int)` | Yes (`char` as `i16`) |
| `==` | Reference identity |
| `equals(String)` | Content compare (runtime) |

## `char`

`PrimitiveType.CHAR` → LLVM `i16`. `println(char)` prints the character when it
is printable ASCII; otherwise integer-like behavior may apply — documented as
minimal for 0.4.
