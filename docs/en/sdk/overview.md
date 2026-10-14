# SDK overview

The “SDK” is the set of **stdlib modules** under `src/main/resources/stdlib/`, automatically available when your program references their types (the frontend pulls them into compilation).

## Maturity (honest)

| Module | Purpose | Doc status |
|--------|---------|------------|
| `io` | Files & streams | Experimental — use with tests |
| `http` | HTTP/1.1 client & server on TCP | Implemented samples; treat as evolving |
| `net` | TCP sockets | Implemented |
| `collections` | Lists/maps/sets, builders | Experimental / widely used |
| `json` | JSON values / codecs | Experimental |
| `time` | Time utilities | Experimental |
| `async` | Async primitives | Experimental |
| `tls` / `crypto` / `db` | TLS, crypto, DB drivers | **Unverified / incomplete** for production — see release blockers |
| `ui` | Desktop UI | Experimental (macOS samples) |
| `web` / `webui` / `browser` | Higher-level web & WASM helpers | Experimental |
| `math` / `rpc` / `graphics` / `editor` / `studio` | Extended | Unverified API-by-API |

**Rule:** Prefer APIs exercised by `src/test/java/io/jnative/e2e` or verified examples. Flagged crypto KDF defaults are unsafe for production ([`../../1.0-release-blockers.md`](../../1.0-release-blockers.md)).

## Pages

[files](files.md) · [io](io.md) · [networking](networking.md) · [http](http.md) · [collections](collections.md) · [json](json.md) · [date-time](date-time.md) · [threading](threading.md) · [synchronization](synchronization.md) · [ffi](ffi.md) · [runtime](runtime.md)
