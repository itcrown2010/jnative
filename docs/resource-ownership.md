# Resource Ownership (Round 2)

## Categories

| Kind | Meaning | Cleanup |
|---|---|---|
| **Managed** | GC-managed heap object | GC |
| **Owned Native** | Process owns OS/native resource | Deterministic `close()` / `free()` |
| **Borrowed Native** | Non-owning view/pointer | Must not free |
| **Shared** | Explicit shared ownership | Documented protocol |

## AutoCloseable / try-with-resources

- Language: `try (T r = open()) { ... }`
- Resources closed in **reverse** declaration order
- Body exception takes precedence over `close()` failure (no `addSuppressed` yet — close exception discarded if body already failed)
- Prefer TWR over relying on GC for native/OS resources

## Owned Native examples

| Resource | Close API | Notes |
|---|---|---|
| `NativeMemory` | `free(ptr)` | Unsafe; double-free undefined |
| TCP / TLS sockets | `.close()` | Manual or TWR when wrapped |
| `WatchService` | `.close()` | |
| DB connections | pool return / close | Higher layers |

## Borrowed Native

- `NativePtr` obtained via `addressOf` / FFI return that is not transferred
- Must not `NativeMemory.free`

## Double close / use-after-close

| Policy (Round 2) | |
|---|---|
| File path APIs | Open-close per call — N/A |
| User AutoCloseable | Should be idempotent when practical |
| Debug ResourceTracker | Counts open/close; exit leak report when enabled |

## Finalizers / Cleaner

Not used as primary cleanup. Deterministic `close` / TWR is required.
