# Building an application

**Goal:** prove what JNative can build **today** with a slightly larger app than the 30-minute Todo.

**Chosen project:** Notes CLI + optional HTTP read API — both use verified File/HTTP APIs.

**Status:** File I/O Experimental · HTTP Experimental · native only for this tutorial.

## Part A — Notes CLI (required)

Follow [build-your-first-application.md](build-your-first-application.md) and `examples/notes-cli/`.

```bash
java -jar target/jnative.jar examples/notes-cli/src/Main.jn -o notes
./notes create shopping "milk"
./notes read shopping
./notes list
./notes delete shopping
```

You now have create/read/list/delete with file persistence.

## Part B — One-shot HTTP status (optional)

Reuse the verified Hello HTTP server pattern (`examples/HttpHelloServer.java` / `examples/http-server/`) to expose a health string:

```bash
java -jar target/jnative.jar examples/HttpHelloServer.java -o HttpHello
./HttpHello   # terminal A
curl -s http://127.0.0.1:18081/   # terminal B → Hello JNative
```

Wiring Notes storage into HTTP handlers is possible with `HttpServer.accept` loops; keep validation explicit. Do **not** assume cookies, sessions, or a framework.

## Part C — What not to attempt yet

| Idea | Reality |
|------|---------|
| Production auth + DB | Blockers / Experimental — see release blockers |
| Full mobile native UI | **Not Supported** — [mobile/status](../mobile/status.md) |
| WebSocket chat | **Not Supported** |
| Drop-in Spring clone | **Not Supported** |

## Architecture reminder

```text
Your app (Notes / HTTP)
   → JNative SDK (File, HttpServer, …)
   → Native runtime
   → OS
```

## Next platforms

- Desktop GUI (Experimental, macOS): [../desktop/index.md](../desktop/index.md)  
- Browser wasm (Experimental): [../web/index.md](../web/index.md)  
- Capability truth: [../reference/capability-matrix.md](../reference/capability-matrix.md)  
