# JNative 0.22 Build Report — Web Stack & Examples

## Summary

- Product version **0.22**
- **Web stdlib**: `stdlib/web/WebLib.java` — routing, middleware chain, static files (`PathSafety`), CORS, JSON response helpers, `WebServer` accept loop
- Builds on **HttpLib** (0.8+) and **JsonLib** (0.21+); **no compiler web intrinsics**
- Shipped examples under `examples/web/` compiled to Mach-O arm64 binaries in `build/web/`
- All three new deliverables **verified with curl** from repo root

```text
NO JVM / NO JRE / NO JNI   (shipped web binaries)
NO Servlet / NO Spring / NO Netty
Web stack written in JNative: YES
JNative compiles web apps → native executables: YES
Web compiler intrinsics: NONE
```

---

## Architecture Acceptance

| Layer | Component | Status |
|-------|-----------|--------|
| TCP | `TcpServer` / `TcpSocket` | PASS (HttpLib) |
| HTTP | `HttpServer`, `HttpConnection`, framing | PASS |
| Web | `WebApp`, `Router`, path params | PASS |
| Middleware | `MiddlewareChain`, `WebNext`, logger, CORS | PASS (field-assign after empty ctor) |
| Static | `staticFiles`, `PathSafety`, `MimeTypes` | PASS |
| JSON | `Json.parse` / `Json.stringify`, `JsonCodec<T>` | PASS |
| Server loop | `WebServer.listen` → `acceptLoop` → `serveConnection` | PASS (acceptor-thread) |

```text
Application (examples/web/*/App.java)
        ↓
WebApp / WebHandler / Middleware
        ↓
HttpLib (HttpServer, HttpConnection)
        ↓
TcpServer + native network runtime
```

Stdlib inclusion: `StdlibIncluder.needsWeb()` pulls `WebLib.java`; `needsJson()` / `needsHttp()` pull JSON and HTTP as needed.

---

## Examples Built

| Example | Source | Binary | Port | Notes |
|---------|--------|--------|------|-------|
| Hello | `examples/web/hello/App.java` | `build/web/hello` (~338 KB) | 18080 | text + JSON greet + echo POST |
| REST CRUD | `examples/web/rest/App.java` | `build/web/rest` (~356 KB) | 18180 | in-memory `/api/items` |
| Static site | `examples/web/staticsite/` | `build/web/staticsite` (~336 KB) | 18181 | `public/` + `/api/info` |
| Todo | `examples/web/todo/` | `build/web/todo` (~355 KB) | 18182 | REST + browser UI in `public/` |

Compile recipe (from repo root):

```bash
mkdir -p target/classes/stdlib/{web,http,json}
cp src/main/resources/stdlib/web/WebLib.java target/classes/stdlib/web/
cp src/main/resources/stdlib/http/HttpLib.java target/classes/stdlib/http/
cp src/main/resources/stdlib/json/JsonLib.java target/classes/stdlib/json/

java -cp target/classes io.jnative.Main examples/web/rest/App.java -o build/web/rest
java -cp target/classes io.jnative.Main examples/web/staticsite/App.java -o build/web/staticsite
java -cp target/classes io.jnative.Main examples/web/todo/App.java -o build/web/todo
```

Run binaries from **repository root** so relative static paths resolve.

---

## curl Verification (2026-10-06)

### REST (`18180`)

```bash
./build/web/rest &
curl -s http://127.0.0.1:18180/api/health
# {"status":"ok"}

curl -s http://127.0.0.1:18180/api/items
# [{"id":"1","name":"alpha"},{"id":"2","name":"beta"}]

curl -s -X POST http://127.0.0.1:18180/api/items \
  -H 'Content-Type: application/json' -d '{"name":"gamma"}'
# {"id":"3","name":"gamma"}

curl -s -o /dev/null -w '%{http_code}\n' -X DELETE http://127.0.0.1:18180/api/items/2
# 204

curl -s http://127.0.0.1:18180/api/items
# [{"id":"1","name":"alpha"},{"id":"3","name":"gamma"}]
```

### Static site (`18181`)

```bash
./build/web/staticsite &
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18181/           # 200
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18181/about.html # 200
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18181/style.css  # 200
curl --path-as-is -s -o /dev/null -w '%{http_code}\n' \
  'http://127.0.0.1:18181/about/../../../etc/passwd'                        # 400 (PathSafety)
```

### Todo (`18182`)

```bash
./build/web/todo &
curl -s -X POST http://127.0.0.1:18182/api/todos \
  -H 'Content-Type: application/json' -d '{"title":"buy milk"}'
# {"id":"1","title":"buy milk","done":false}

curl -s http://127.0.0.1:18182/api/todos
# [{"id":"1","title":"buy milk","done":false}]

curl -s http://127.0.0.1:18182/ | head -1
# <!DOCTYPE html>
```

---

## Source Files Added

```text
examples/web/README.md
examples/web/rest/App.java
examples/web/staticsite/App.java
examples/web/staticsite/public/index.html
examples/web/staticsite/public/about.html
examples/web/staticsite/public/style.css
examples/web/todo/App.java
examples/web/todo/public/index.html
examples/web/todo/public/style.css
examples/web/todo/public/app.js
docs/jnative-0.22-build-report.md
```

Supporting stdlib touch-up:

```text
src/main/resources/stdlib/web/WebLib.java   — root static mount matching (`urlPrefix "/"`)
src/main/resources/stdlib/http/HttpLib.java — last request header (Content-Length) parse fix (prior)
```

---

## Honest Limitations

1. **Acceptor-thread serve** — `WebServer.acceptLoop()` calls `serveConnection()` on the acceptor thread. Throughput is adequate for demos; not production-grade concurrency.

2. **Thread pool created but unused for connections** — `ThreadPoolExecutor` is constructed in `startListener()`, but `WebConnTask` / `PoolTask` constructors with captured fields emit **empty bodies** under current generics/ctor lowering. Pool is kept for API shape; work is not dispatched to workers yet.

3. **MiddlewareChain / WebNext field assignment** — generic/empty ctor bodies require manual field assignment after `new` (see `WebApp.dispatch` and `MiddlewareChain` usage in examples). Do not rely on constructor parameters for `ArrayList<Middleware>` etc.

4. **Single-process, in-memory state** — REST and Todo stores reset on restart; no persistence.

5. **No TLS / HTTP/2** — plain HTTP on `127.0.0.1` only in examples.

6. **Static paths are cwd-relative** — run from repo root or adjust `staticFiles(...)` paths.

7. **Response header ordering** — set headers (including CORS) **before** committing body via `text`/`jsonValue`/`fileBody`; `Connection` is applied on `HttpResponse` **after** `toHttp()` inside `WebServer`.

8. **Language subset** — examples avoid ternary, char literals, method overloading, anonymous classes; use named `WebHandler` classes and `catch (Exception)` where I/O requires it.

9. **PathSafety** — blocks `..` and backslashes; traversal probes return **400** when `--path-as-is` is used (curl may normalize `../` otherwise).

---

## Final Status

```text
# A. JNATIVE_0_22_WEB_EXAMPLES_PASS

hello:       PASS (pre-existing)
rest:        PASS
staticsite:  PASS
todo:        PASS
```
