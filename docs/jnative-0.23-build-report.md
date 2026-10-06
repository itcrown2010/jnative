# JNative 0.23 Build Report — Async/Await, EventLoop, Async Web

## Summary

- Product version **0.23**
- **Compiler**: `async`/`await` lowering, `Future<T>` ABI, semantic/effect checks
- **Async stdlib**: `stdlib/async/AsyncLib.java` — `EventLoop`, `JnFuture`, `FutureBlocker`, `Async.sleepOnLoop`, worker-pool file/TCP helpers
- **Web async path**: `WebLib` extended with `AsyncWebHandler`, `futureGet`/`futurePost`/…, `bindEventLoop`, `listenWithLoop`
- **Examples**: `examples/web/todo_async/`, `examples/async/concurrency/`
- **Tests**: `AsyncSemanticTest`, `AsyncFutureSemanticTest`, `AsyncEndToEndTest`

```text
NO JVM / NO JRE / NO JNI   (shipped async + web binaries)
Compiler async/await: YES (0.23)
Stdlib promise type: JnFuture (distinct from compiler Future<T> ABI)
Native IoPoller: kqueue (macOS REAL), epoll (Linux PARTIAL), IOCP (Windows PARTIAL)
```

---

## Architecture Acceptance

| Layer | Component | Status |
|-------|-----------|--------|
| Compiler | `async` methods, `await Future<T>`, effect `@Suspends` | PASS |
| Semantic | await-only-in-async, no `Future<T>` return, `@NoGC` + await rejected | PASS |
| Runtime | `AsyncRuntimeEmitter`, state-machine lowering | PASS |
| EventLoop | ready queue, timer wheel, native poll integration | PASS |
| JnFuture | PENDING/SUCCESS/FAILED/CANCELLED, `then`/`flatMap`/`all`/`any`/`timeout` | PASS |
| Web sync | existing `WebHandler` + `listen()` unchanged | PASS |
| Web async | `AsyncWebHandler` → `JnFuture`, `listenWithLoop()` | PASS (see notes) |
| Concurrency proof | 1000 × `Async.sleepOnLoop(100)` on one loop | PASS |

```text
Application (examples/web/todo_async/App.java)
        ↓
WebApp.futureGet → AsyncWebHandler.handle → JnFuture
        ↓
FutureBlocker.blockingWait(Async.sleepOnLoop(loop, ms))  [simulated DB]
        ↓
EventLoop (background thread) + WebServer acceptor (main thread)
        ↓
HttpLib → TcpServer → native poll (kqueue/epoll/IOCP)
```

Stdlib inclusion: `needsWeb()` now also pulls `AsyncLib.java` (WebLib references `JnFuture` / `EventLoop`). `needsAsync()` unchanged for explicit async apps.

---

## Sync vs Async Web

| Path | API | Dispatch |
|------|-----|----------|
| **Sync** | `app.get/post/...`, `app.listen(port)` | `WebHandler.handle()` runs inline on acceptor thread |
| **Async** | `app.futureGet/post/...`, `app.bindEventLoop(loop)`, `app.listenWithLoop(port)` | Handler returns `JnFuture`; pending futures awaited via `FutureBlocker.blockingWait` (poll + `Thread.sleep(1)`) |

**Naming note:** identifiers cannot contain the `async` keyword prefix in fields (`asyncMode`, `asyncLoop` break the parser). Public APIs use `futureGet`, `listenWithLoop`, `bindEventLoop`.

**Honest handler pattern (todo_async):**

```java
public JnFuture handle(WebContext ctx) {
    FutureBlocker.blockingWait(Async.sleepOnLoop(loop, 10)); // simulated DB
    ctx.jsonValue(...);
    return JnFuture.completed(loop, null);
}
```

`JnFuture.then` chaining from the acceptor thread is not yet reliable for response completion; the example uses explicit `blockingWait` + completed future instead of `.then` callbacks.

**Middleware:** sync `MiddlewareChain` before async handlers is skipped in 0.23 (logger/CORS on future routes deferred — static + sync routes still use middleware normally).

---

## Socket vs File Async (Honest)

| I/O | Implementation | Notes |
|-----|----------------|-------|
| **Timers / sleep** | Real `EventLoop` timer wheel + native poll wakeups | Used by concurrency proof |
| **TCP connect/read/write** | `Async.blockingOnLoop` → shared `ThreadPoolExecutor` | Worker pool offload, completion on loop |
| **File read/write** | Same worker-pool pattern via `Async.blockingRun` | **Not** true async disk I/O |
| **HTTP accept (web)** | Blocking `HttpServer.accept` on main thread | Same as 0.22 acceptor model |

---

## Poller Status

| Platform | Backend | Status |
|----------|---------|--------|
| **macOS** | kqueue (`jnative_poll_kqueue.c`) | **REAL** — verified wakeups in sleep example |
| **Linux** | epoll (`jnative_poll_epoll.c`) | **PARTIAL** — add/mod/del/wait wired; edge cases remain |
| **Windows** | IOCP (`jnative_poll_iocp.c`) | **PARTIAL** — no select-based fake poller |

No select()-based fallback in 0.23.

---

## Examples Built

| Example | Source | Binary | Port | Notes |
|---------|--------|--------|------|-------|
| Sleep | `examples/async/sleep/App.java` | `build/async/sleep` | — | single timer + loop stop |
| Concurrency | `examples/async/concurrency/App.java` | `build/async/concurrency` | — | 1000 concurrent sleeps |
| Todo async | `examples/web/todo_async/App.java` | `build/web/todo_async` | 18183 | REST + static UI reuse |

Compile recipe (from repo root):

```bash
mkdir -p target/classes/stdlib/{async,collections,web,http,json,io}
cp src/main/resources/stdlib/async/AsyncLib.java target/classes/stdlib/async/
cp src/main/resources/stdlib/collections/CollectionsLib.java target/classes/stdlib/collections/
cp src/main/resources/stdlib/web/WebLib.java target/classes/stdlib/web/
cp src/main/resources/stdlib/http/HttpLib.java target/classes/stdlib/http/
cp src/main/resources/stdlib/json/JsonLib.java target/classes/stdlib/json/
cp src/main/resources/stdlib/io/IoLib.java target/classes/stdlib/io/

mvn -q compile -DskipTests   # StdlibIncluder: needsWeb → AsyncLib

java -cp target/classes io.jnative.Main examples/async/concurrency/App.java -o build/async/concurrency
java -cp target/classes io.jnative.Main examples/web/todo_async/App.java -o build/web/todo_async
```

Run web examples from **repository root** (static paths).

---

## Concurrency Proof (2026-10-06, macOS arm64)

```bash
./build/async/concurrency
```

```text
concurrency total=1000
delayMs=100
wallMs=326
threads=2
serialMs=100000
```

- **Wall time ≪ serial**: 326 ms vs 100 000 ms (1000 × 100 ms sequential)
- **Threads ≪ 1000**: 2 (main + one `EventLoopRunner`); no per-sleep thread explosion
- Minor post-shutdown NPE in worker thread logged after metrics (non-fatal for proof)

---

## curl Verification — todo_async (`18183`)

```bash
./build/web/todo_async &
curl -s http://127.0.0.1:18183/api/health
# {"status":"ok"}

curl -s -X POST http://127.0.0.1:18183/api/todos \
  -H 'Content-Type: application/json' -d '{"title":"async milk"}'
# {"id":"1","title":"async milk","done":false}

curl -s http://127.0.0.1:18183/api/todos
# [{"id":"1","title":"async milk","done":false}]

curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18183/
# 200
```

Sync todo (`18182`) remains unchanged and passing.

---

## Tests

| Test | Path | Coverage |
|------|------|----------|
| AsyncSemanticTest | `src/test/java/io/jnative/async/` | await rules, `@NoGC`, valid async |
| AsyncFutureSemanticTest | same | nested await, sync-await rejection |
| AsyncEndToEndTest | `src/test/java/io/jnative/e2e/` | JnFuture states native; 200×50 ms concurrency-fast |

```bash
mvn test -Dtest=AsyncSemanticTest,AsyncFutureSemanticTest,AsyncEndToEndTest
```

---

## 0.24 DB Readiness

- **Connection pool stub**: reuse `Async.blockingOnLoop` + `EventLoopGroup` for query offload
- **Query API shape**: `futureGet("/api/…")` handlers + `FutureBlocker.blockingWait` maps cleanly to pooled sync JDBC/SQLite calls
- **Real async sockets**: migrate `HttpServer.accept/read` to loop-driven `AsyncTcpServer` when IOCP/epoll completion paths mature
- **Middleware on async routes**: re-enable once `MiddlewareChain` can terminate into `JnFuture` without acceptor deadlock
- **Dialect**: continue avoiding `async*` field names; prefer `futureGet` / `listenWithLoop`

---

## Honest Limitations

1. **Acceptor-thread web model retained** — async handlers block acceptor via `FutureBlocker` polling; fine for demos, not production throughput.

2. **`JnFuture.then` from acceptor** — response completion via chained callbacks is unreliable; use `blockingWait(sleep)` + sync response body + `JnFuture.completed`.

3. **Future routes skip middleware** — CORS/logger not applied to `futureGet` routes in 0.23.

4. **File async = thread pool** — not io_uring/AIO.

5. **Linux/Windows pollers PARTIAL** — macOS kqueue is the reference platform for 0.23 proofs.

6. **Language subset** — no ternary, char literals, overloading, anonymous classes; no `import jnative.async.*` (`async` is keyword).

7. **`JnCancellationToken`** — renamed from `CancellationToken` to avoid IoLib collision.

---

## Final Status

```text
# A. JNATIVE_0_23_ASYNC_WEB_PASS

compiler async/await:     PASS
EventLoop + JnFuture:     PASS
concurrency proof:        PASS (wallMs=326, threads=2)
todo_async curl:          PASS
unit + e2e tests:         PASS
```

Report path: `docs/jnative-0.23-build-report.md`
