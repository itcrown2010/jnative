# What Can I Build?

Grouped by **actual** capability (2026-10-13 audit). Labels: **Current** · **Experimental** · **Future**.

## CLI

**Current.** Console programs with `main`, args, `System.out`.

- `examples/hello-world/`, `examples/Hello.java`
- Tutorial: [build-your-first-application](../tutorials/build-your-first-application.md) (Notes CLI)

## System Utilities

**Current / Experimental.** File read/write/delete via `File.*`.

- `examples/file-io/`, `examples/notes-cli/`

## Network Applications

**Experimental (native).** TCP echo, HTTP client/server.

- `examples/tcp-echo/`, `examples/EchoServer.java`
- `examples/http-server/`, `examples/HttpHelloServer.java`, `examples/HttpDownload.java`

## Web Services

**Experimental.** Native process HTTP server + optional `WebApp` routing—not a full enterprise framework.

- `examples/web/hello`
- Docs: [web/capability-matrix](../web/capability-matrix.md)

Browser **wasm** apps with JS shells: `examples/guide-tasks` (**Experimental**).

## Desktop Applications

**Experimental (macOS).** Native UI samples exist; Win/Linux GUI unverified.

- `examples/desktop-sample/`, `examples/notepad/`, activity-monitor

## Mobile Applications

**Experimental container only.** WebView + wasm—**not** a production native UI toolchain.

- `examples/guide-tasks/mobile/`, sudoku mobile shells  
- See [mobile/capability-matrix](../mobile/capability-matrix.md)

## Browser Applications

**Experimental.** `wasm32-browser` core + JS glue.

- guide-tasks web / sudoku web

## Future

- Production auth/DB hardening  
- Native mobile UI SDK  
- Verified Windows/Linux desktop CI  
- Full package-manager publish workflow tutorials  

Do not plan products that require **Not Supported** rows in the [capability matrix](../reference/capability-matrix.md).
