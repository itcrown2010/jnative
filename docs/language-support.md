# JNative Language Support

JNative implements a **subset** of Java. It is **not** a Java SE implementation.

## Supported (0.6)

### From 0.1–0.5

Primitives, control flow, classes, inheritance, vtable, constructors, arrays, String, RTTI, precise GC, `System.gc()`.

### New in 0.6

- `throw` / `try` / `catch` / `finally`
- Builtin `Throwable` hierarchy (+ NPE, AIOOBE, …)
- User-defined exceptions via normal `extends`
- Runtime failures promoted to typed exceptions
- Explicit exception propagation (not zero-cost EH)
- Checked-exception static enforcement: **not** implemented

## Not Supported (yet)

- `interface`, generics, lambdas, try-with-resources, multi-catch `A | B`
- Full stack traces / suppressed exceptions
- Full standard library / threads / synchronized / reflection

## Encoding note

String payload is **UTF-8** in 0.4 (documented; runtime-isolated). Future: UTF-16.

## Type / ABI notes

| Java | Native |
|------|--------|
| class T | ptr + `%class.T`; header = `ClassMetadata*` |
| constructor | `_JN_T_init[_sig](ptr this, ...)` |
| instance method | vtable via metadata |
| `T[]` | array object `{ meta*, length, elements… }` |
| `String` | `{ meta*, i32 len, ptr data }` |
