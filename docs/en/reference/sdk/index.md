# SDK Reference

Public APIs come from:

1. **Compiler builtins** installed in `SemanticAnalyzer` (e.g. `File`, `TcpSocket`, `Thread`)  
2. **Stdlib modules** under `src/main/resources/stdlib/`

Maturity: treat all non-console APIs as **Experimental** unless the capability matrix says Stable.

| Module docs | Source |
|-------------|--------|
| [console / runtime](runtime.md) | runtime + `System` |
| [filesystem](filesystem.md) | File builtins + `io/IoLib` |
| [networking](networking.md) | Tcp builtins + `net/` |
| [http](http.md) | `http/HttpLib.java` |
| [json](json.md) | `json/JsonLib.java` |
| [collections](collections.md) | `collections/` |
| [threading](threading.md) | Thread / Mutex builtins |
| [synchronization](synchronization.md) | Mutex / atomics |
| [ffi](ffi.md) | FFI annotations + native |
| [datetime](datetime.md) | `time/` |
| Narrative SDK guides | [`../../sdk/`](../../sdk/overview.md) |

**Do not invent signatures.** Prefer reading the `.java` stdlib file or generated extracts under `website/api/_generated/` when present.
