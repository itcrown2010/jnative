# Web development

**Status:** Experimental

JNative can:

1. Run a **native** HTTP server (stdlib `HttpServer` / `WebApp`) as a native executable.
2. Export **wasm32-browser** cores for browser/WebView shells (guide-tasks, sudoku).

It is **not** Node.js and **not** a servlet container.

## Start here

| Goal | Doc / example |
|------|----------------|
| Minimal listen/accept/respond | [http-server.md](http-server.md), `examples/http-server/` |
| Routed WebApp + JSON | [rest-api.md](rest-api.md), `examples/web/hello/` |
| Full tutorial | [../tutorials/web-app.md](../tutorials/web-app.md) |
| Browser wasm shell | `examples/guide-tasks/` (tasks UI) |

## Not currently supported (as a first-class story)

- Automatic HTML template engines (beyond what you emit as strings/files)
- Full production auth/DB stacks without reading release blockers
- Claiming “deploy anywhere” without OS-specific testing
