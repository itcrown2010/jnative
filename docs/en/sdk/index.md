# SDK Platform Guide

The **SDK** is the standard library and platform APIs your program calls.  
It is **not** the language grammar and **not** the compiler.

```text
Application
     │
     ▼
JNative SDK  (stdlib modules + builtins)
     │
     ├── Core / console
     ├── Collections & strings
     ├── IO / filesystem
     ├── Networking / HTTP
     ├── Concurrency
     ├── JSON / time / …
     └── FFI / platform UI / web helpers
            │
            ▼
       Native Runtime (GC, threads, sockets, …)
            │
            ▼
       Operating System
```

| Layer | Owns |
|-------|------|
| Language | Syntax, types, classes, generics |
| Compiler | Frontend → IR → LLVM |
| SDK | `File`, `HttpClient`, `ArrayList`, … |
| Runtime | GC, thread primitives, native I/O emitters |
| Platform | OS APIs, UI toolkits, wasm host glue |

## Guide pages

| Page | Status |
|------|--------|
| [Overview](overview.md) | map of modules |
| [Getting started](getting-started.md) | first SDK calls |
| [Core](core.md) | console / runtime |
| [IO](io.md) | streams |
| [Filesystem](filesystem.md) | `File.*` |
| [Networking](networking.md) | TCP |
| [HTTP](http.md) | client/server |
| [Concurrency](concurrency.md) | threads / mutex |
| [Collections](collections.md) | lists/maps |
| [Strings](strings.md) | string helpers |
| [Time](time.md) | date/time |
| [JSON](json.md) | parse/stringify |
| [FFI](ffi.md) | native calls |
| [Platform](platform.md) | OS / target limits |
| [Error handling](error-handling.md) | failures |

Structured API sketches: [reference/sdk/](../reference/sdk/index.md)  
Generated index (when present): [`../_generated/sdk-index.json`](../_generated/sdk-index.json)
