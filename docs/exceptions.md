# JNative 0.6 — Exceptions

## Overview

JNative implements Java exception semantics with **explicit pending-exception propagation**.
Exception objects are ordinary GC-managed instances with `ClassMetadata` and normal inheritance.

Checked-exception static enforcement (`throws` / catch-or-declare) is **not implemented in 0.6**.

## Throwable Hierarchy

```text
Object
  └── Throwable          (field: String message; getMessage())
        └── Exception
              └── RuntimeException
                    ├── NullPointerException
                    ├── ArrayIndexOutOfBoundsException
                    ├── NegativeArraySizeException
                    ├── ClassCastException
                    └── ArithmeticException
```

User classes may `extends Exception` / `RuntimeException` like ordinary classes.

## Frontend

- `throw expr;` — expression must be a `Throwable` subtype (`throw null` → NPE at runtime)
- `try` / `catch` / `finally` — multiple catch clauses in source order
- Unreachable catch (subtype after supertype) is a semantic error
- `throws` syntax may be parsed later; 0.6 does not enforce it

## Exception IR

IR-level ops (not AST→LLVM shortcuts):

- `Throw`
- pending checks after potentially throwing ops (`Call`, `VirtualCall`, ctor, null/bounds/cast, div)
- structured try dispatch / catch / finally CFG in `IrGenerator`

LLVM backend only lowers these ops; it does **not** resolve catch ordering or Java subtyping.

## Propagation Model

Runtime thread-ready state (global in 0.6, future per-thread):

```text
@jn.ex.current   ; GC root
@jn.ex.pending
```

After a call (or other throwing op):

```text
if (jnative_exception_pending())
    goto handler_or_unwind_return
```

No `setjmp`/`longjmp`. Future backends may switch to LLVM `invoke`/`landingpad` without changing frontend semantics.

## Catch Matching

Uses existing `ClassMetadata` superclass chain / `jnative_instanceof` — the same subtype system as ordinary inheritance.

## Finally Model

Unified abrupt completion:

```text
NORMAL | RETURN | THROW
```

Finally always runs. If finally completes normally, the saved completion resumes.
`return` / `throw` inside finally override the pending completion.
Return-in-finally does not re-enter the same finally block.

## Runtime Failures → Exceptions

| Failure | Exception |
|---------|-----------|
| null dereference | `NullPointerException` |
| array bounds | `ArrayIndexOutOfBoundsException` |
| negative array size | `NegativeArraySizeException` |
| invalid cast | `ClassCastException` |
| int/long division by zero | `ArithmeticException` |

## Uncaught

If pending when `main` returns:

```text
Exception in thread "main":
<ClassName>
<message>
exit(1)
```

Full stack traces are not required in 0.6.

## GC Integration

- `@jn.ex.current` is scanned in `jnative_gc_mark_from_roots`
- Catch locals are ordinary PTR allocas on the shadow stack
- Exception message is a JNative `String` reference

## Allocation Failure

0.6 does not implement `OutOfMemoryError`. Future work must avoid recursive OOM allocation.

## Future Native EH

Architecture allows replacing explicit checks with LLVM native EH / SEH while keeping the same IR exception regions and Java matching rules.
