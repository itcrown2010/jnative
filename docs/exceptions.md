# JNative — Exceptions & try-with-resources

作者：张晓达

## Overview

JNative implements Java-compatible exception semantics with **explicit pending-exception
propagation** (thread-local TLS). Exception objects are ordinary GC-managed instances with
`ClassMetadata` and normal inheritance. There is no parallel exception mechanism.

**Checked-exception static enforcement** (`throws` / catch-or-declare) is **not implemented**.
The `throws` clause is not accepted by the parser (hard error). Unchecked and user-defined
`Throwable` subtypes behave at runtime like Java; missing `throws` is never silently ignored
as “checked OK”.

## Throwable hierarchy

```text
Object
  └── Throwable          (message, cause, suppressed[]; getMessage/getCause/addSuppressed/getSuppressed)
        └── Exception
              ├── IOException
              │     ├── FileNotFoundException
              │     └── SocketException / ConnectException / UnknownHostException / SocketTimeoutException
              └── RuntimeException
                    ├── NullPointerException
                    ├── ArrayIndexOutOfBoundsException
                    ├── NegativeArraySizeException
                    ├── ClassCastException
                    ├── ArithmeticException
                    ├── IllegalArgumentException
                    ├── IllegalStateException
                    └── IllegalMonitorStateException
```

User classes may `extends Exception` / `RuntimeException` like ordinary classes.

Layout (native): `{ ClassMeta* @0, message* @8, cause* @16, suppressed* @24 }` — **32 bytes**.

## Frontend

| Feature | Status |
|---------|--------|
| `throw expr;` (`Throwable` subtype) | Yes |
| `throw null;` | Runtime `NullPointerException` |
| `try` / `catch` / `finally` | Yes |
| Catch order + subtype matching | Yes (`instanceof` / superclass chain) |
| Unreachable catch | Semantic error |
| Catch variable scope | Local to catch body; effectively treated as assigned |
| Multi-catch `catch (A \| B e)` | **Unsupported** (parse error) |
| Precise rethrow | **Unsupported** (no special typing) |
| `throws` clause | **Unsupported** (parse error; no checked-exception checking) |
| try-with-resources (declaration form) | Yes |
| try-with-resources (existing variable) | **Not yet** |
| Resource implicitly final | Yes |

## Propagation model

Runtime TLS (per OS thread):

```text
jn_tls_ex_current / pending     ; GC root for current exception
jn_tls_ex_return_pending/value  ; abrupt completion: return / break / continue
```

After a potentially throwing op:

```text
if (jnative_exception_pending())
    goto handler_or_unwind_return
```

No `setjmp`/`longjmp`. LLVM backend lowers IR checks; it does not invent catch ordering.

### Cross-thread / FFI limits

- Pending exception state is **per-thread**. Uncaught exceptions in worker threads are reported
  via `jnative_exception_uncaught_worker` and cleared; they do not cross into another thread’s TLS.
- FFI / native calls must not let raw native unwinding cross the managed boundary. Native helpers
  convert errno-style failures into JNative exceptions (`jnative_exception_throw`) before returning.
  A native crash (SIGSEGV etc.) is reported separately from language exceptions.

## Catch matching

Uses `ClassMetadata` superclass chain / `jnative_instanceof` — same subtype system as ordinary
inheritance. Clauses are tried in source order. Unmatched exceptions keep pending and continue
to finally / outer handlers.

## Finally & abrupt completion

Unified completion kinds: `NORMAL | RETURN | BREAK | CONTINUE | THROW`.

Rules:

1. Finally always runs for normal and abrupt exits from the protected region.
2. Return expression is evaluated **before** entering finally; the value is saved in completion TLS.
3. If finally completes normally, the saved completion resumes (return / break / continue / throw).
4. `return` / `throw` / break / continue inside finally **replace** the prior completion (Java rules).
5. The same finally block is not re-entered for a return that originates inside that finally
   (`finallyDepth` skip).
6. Nested finally blocks run innermost-first.

## try-with-resources

Declaration form:

```java
try (Resource first = openFirst();
     Resource second = openSecond()) {
    use(first, second);
} catch (E e) { ... } finally { ... }
```

Semantics (aligned with JLS §14.20.3):

1. Resources initialize in declaration order inside the protected region.
2. On any exit, resources close in **reverse** order.
3. If a later initializer throws, earlier resources still close.
4. `null` resources skip `close()`.
5. Close runs **before** catch and **before** user `finally`.
6. Resource types must implement `AutoCloseable` or declare `void close()`.
7. Resource variables are implicitly `final`.

Implemented as nested try frames: inner frame closes resources; outer frame owns catch + user finally.

## Suppressed exceptions

| Situation | Result |
|-----------|--------|
| Body/primary exception + close throws | `primary.addSuppressed(closeEx)`; primary remains pending |
| No primary; first close throws | That exception becomes primary |
| Later close throws | Attached via `addSuppressed` on current primary |
| `addSuppressed(null)` | `NullPointerException` |
| `addSuppressed(this)` | `IllegalArgumentException` |

API:

```java
void addSuppressed(Throwable exception);
Throwable[] getSuppressed();  // defensive copy; empty array if none
```

Suppressed array references are ordinary object fields scanned by GC. Identity of primary and
suppressed throwables is preserved (not stringified).

## Runtime failures → exceptions

| Failure | Exception |
|---------|-----------|
| null dereference | `NullPointerException` |
| array bounds | `ArrayIndexOutOfBoundsException` |
| negative array size | `NegativeArraySizeException` |
| invalid cast | `ClassCastException` |
| int/long division by zero | `ArithmeticException` |

## Uncaught

If pending when `main` returns: print class name, message, stack symbols, `exit(1)`.
Debugger hook: `jnative_debugger_on_uncaught`.

## GC

- TLS current exception is a GC root.
- Catch locals and resource locals are PTR allocas on the shadow stack.
- `message`, `cause`, and `suppressed` fields are reference fields on `Throwable`.

## Optimization & debug

- Optimizers must not delete resource closes, finally side effects, or pending-exception checks.
- Abrupt completion values and pending throwables remain valid across allocation/GC.
- Debug catalogs distinguish user source for try/catch/finally; synthetic close blocks use
  `twr.*` IR names.
- Native crashes vs language exceptions are separate reporting paths.

## WASM / browser backend

wasm32-browser uses the **same** explicit pending-exception + CFG model as native (no WASM EH
opcodes). See `docs/wasm32-browser-exceptions.md` for the JS boundary contract, browser matrix,
and `examples/browser/exc-stress` results.

## Tests

- `ExceptionEndToEndTest` — catch, finally abrupt completions, nesting, NPE on `throw null`,
  unreachable catch, O0 vs default opt.
- `ResourceEndToEndTest` — close order, init failure, null resource, suppressed exceptions,
  return-through-TWR, user finally after close/catch.

## Remaining limits

- No checked-exception checking / `throws`.
- No multi-catch, no precise rethrow.
- No try-with-resources on an existing effectively-final variable.
- No `OutOfMemoryError` on allocation failure.
- wasm32-browser: no WASM EH opcodes; no full browser source maps yet (see wasm32-browser-exceptions.md).
