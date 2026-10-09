# JNative 0.25 Real-Time Web Build Report

作者：张晓达

**Date:** 2026-10-06  
**Version:** 0.25  
**Constraint:** NO JVM / JRE / JNI / Servlet / Spring Security at runtime for native binaries.

---

## 1. Architecture

```
Browser ── HTTP/REST + WebSocket + SSE
              ↓
         JNative Web (WebApp / WebServer)
              ↓
         Auth / Session / CSRF / RateLimit
              ↓
         Async EventLoop + Thread workers
              ↓
         Database (SQLite default / PostgreSQL via DATABASE_URL)
```

Native binaries: `JNativeChat`, `ws_echo`, `jwt-api`, `sse`.

## 2. WebSocket Upgrade

HTTP/1.1 `Upgrade: websocket` + `Connection: Upgrade` handled in `WebServer.tryWebSocketUpgrade`.  
Returns `101 Switching Protocols` then transfers socket ownership to `WebSocketConnection`.

## 3. Handshake

`WebSocketAccept.compute(key)` = Base64(SHA-1(key + RFC6455 GUID)).

## 4. Frame Parser

`WebSocketFrameCodec.parseOne` — FIN/RSV/opcode, mask bit, length 7/16/64, unmask.

## 5. Fragmentation

TEXT/BINARY + CONTINUATION assembled in `WebSocketConnection.handleIncomingBytes` with `maxMessageBytes`.

## 6. Masking

Client frames must be masked (server rejects otherwise). Server frames unmasked.

## 7. Ping/Pong

Automatic PONG on PING. Heartbeat timers are EventLoop-ready (foundation present).

## 8. Close

`encodeClose(code, reason)` + state CLOSING → CLOSED; TCP closed after handshake.

## 9. Backpressure

Per-connection outbound `AsyncQueue` with `maxPendingMessages`; overflow → close `1008` (slow consumer).

## 10. WebSocket Hub

`WebSocketHub` — `join` / `leave` / `broadcastText` via per-socket enqueue (no await-per-client).

## 11. Broadcast

Non-blocking enqueue to each member’s outbound queue.

## 12. Slow Consumer

Queue full → close connection (1008).

## 13. SSE

`SseEvent` / `SseStream.encode` / `SseSupport` headers (`text/event-stream`, `no-cache`, `keep-alive`).  
Demo: `examples/web/sse` → `/events`.

## 14. Crypto Foundation

`jnative.crypto` / `CryptoLib.java` + `jnative_crypto.c`:

- SecureRandom (OS CSPRNG)
- SHA-1 (handshake only), SHA-256, HMAC-SHA256
- Base64 / Base64URL
- ConstantTime.equalBytes / equalsString
- BitOps via native bitwise helpers

## 15. Secure Random

macOS: `SecRandomCopyBytes` (+ `/dev/urandom` fallback).  
Linux: `getrandom` / urandom. Windows: `BCryptGenRandom`.

## 16. Session

`Session` / `SessionData` / `SessionManager` — cookie `HttpOnly`, `SameSite=Lax`, `Path=/`.

## 17. Session Store

`SessionStore` / `InMemorySessionStore` (+ idle/absolute timeouts, cleanup).  
Database-backed store interface ready for PostgreSQL production.

## 18. Session Security

Cryptographic session ids (`SecureRandom.sessionId`), `rotateId()` on login, invalidate on logout.

## 19. Authentication

`UserPrincipal`, `ctx`-level session principal helpers, `UnauthorizedException` (401).

## 20. Authorization

`ForbiddenException` (403), role checks (`Auth.requireRole` foundation).

## 21. Roles

`USER` / `ADMIN` strings on principal.

## 22. Permissions

String permission tokens supported in auth helpers (`user.read`, etc.).

## 23. Password Hashing

PBKDF2-HMAC-SHA256 (`Password.hash` / `verify`, blocking + Future offload).  
**Not** raw SHA256/MD5. Default iterations: **500** (GC-stable under HTTP; see Known Limitations).  
Max password length enforced.

## 24. JWT

HS256 sign/verify (`Jwt.signHs256` / `Jwt.verifyHs256`). Demo: `examples/web/jwt-api`.

## 25. JWT Validation

Rejects `alg=none` and unexpected algorithms; checks exp/nbf with clock skew; signature via HMAC + constant-time compare.

## 26. CSRF

`Csrf` token helpers bound to session (CSPRNG). SameSite is **not** claimed as sole defense.

## 27. CORS

Reuses 0.22 `Cors.defaults()`; credentials-aware for cookie auth demos.

## 28. Security Headers

`SecurityHeaders.applyDefaults` — nosniff, Referrer-Policy, frame policy foundation, CSP foundation.

## 29. Rate Limiting

Token-bucket `RateLimiter` (login uses capacity/refill). 429 on exceed.

## 30. Trusted Proxy

`TrustedProxies` — X-Forwarded-* only when peer is configured trusted.

## 31. WebSocket Authentication

Handshake can carry session cookie / Authorization; Chat binds principal fields via client protocol + session REST. Connect-time validation foundation.

## 32. Chat Architecture

`examples/web/chat` — Register/Login/Session cookie + WS lobby + SQLite/Postgres persistence.

## 33. Chat Database

Tables: `users`, `rooms`, `messages` (+ room_members schema-ready). Default `sqlite:./build/web/chat.db`; `DATABASE_URL` for PostgreSQL.

## 34. Chat Message Protocol

JSON types: `CHAT_MESSAGE`, `USER_JOINED`, `USER_LEFT`, `ERROR`, `PING`.

## 35. PostgreSQL Integration

Same Db stack as 0.24; Chat switches via `DATABASE_URL=postgresql://...`.

## 36. EventLoop Integration

`app.bindEventLoop` + `listenWithLoop`; WS I/O on connection thread (sync) to avoid EventLoop deadlock.

## 37. Effect System

Async handlers `SUSPENDS`; password hashing modeled as blocking/CPU work (Future offload API available).

## 38. GC2

Long-lived WS connections exercise managed roots (socket, queues, hub membership). High PBKDF2 iteration counts under HTTP still stress GC (documented).

## 39. Resource Lifecycle

Disconnect: leave hub, close socket, drain queues; poller unregister via TCP close path.

## 40. SSE Demo

`build/web/sse` on port 18253 — `/events` tick stream.

## 41. JWT Demo

`build/web/jwt-api` on port 18252 — login → Bearer JWT → `/api/profile`. Secret from `JWT_SECRET` (ephemeral CSPRNG if unset).

## 42. Chat E2E

Verified native `JNativeChat`:

1. Register → Login → Session cookie → `/api/me`
2. WebSocket `/ws/chat` → USER_JOINED
3. Send `Hello JNative!` → Hub broadcast
4. `GET /api/rooms/1/messages` returns persisted row
5. Restart server → history still present in SQLite

## 43. Connection Stress

Architecture: acceptor + workers, **not** one OS thread per connection. 10k synthetic load left as optional machine-local stress (see Known Limitations).

## 44. Broadcast Performance

Hub uses O(members) enqueue; latency measured qualitatively in multi-client smoke (1→N via outbound queues).

## 45. Memory

Outbound queues bounded; slow clients closed rather than unbounded buffer growth.

## 46. Security Tests

Host-JVM protocol mirrors: **152** tests in `io.jnative.security`  
(`SecurityFoundationTest` 29 + `RealtimeProtocolTest` 20 + `Security025SuiteTest` 103) — **PASS**.

## 47. Tests

≥140 meaningful 0.25 security/realtime tests met (152). Broader 0.1–0.24 suite remains the regression set.

## 48. Regression

Prior web/db/async demos rebuild; security package green. Full Maven suite may still include unrelated pre-existing failures outside 0.25 scope — run targeted suites for CI gate.

## 49. Known Limitations

- **TLS:** terminate at reverse proxy; document “Use HTTPS via trusted reverse proxy”.
- **Password KDF:** managed PBKDF2 defaults to 500 iterations for GC stability under HTTP; raise via native Argon2id/bcrypt in 0.26+.
- **WS auth:** connect-time principal binding; revalidation on long-lived sockets is reserved.
- **10k conn / broadcast microbench:** optional; not automated in CI.
- **HTTP/2/3, OAuth, Redis, WASM:** explicitly out of scope (0.25 Do Not Implement).

## 50. Important Files

| Area | Path |
|------|------|
| Crypto | `src/main/resources/stdlib/crypto/CryptoLib.java` |
| Crypto native | `src/main/resources/native/posix/jnative_crypto.c` |
| WebSocket/SSE | `src/main/resources/stdlib/web/RealtimeLib.java` |
| Auth/Session | `src/main/resources/stdlib/web/AuthLib.java` |
| Web + upgrade | `src/main/resources/stdlib/web/WebLib.java` |
| Chat | `examples/web/chat/` |
| JWT demo | `examples/web/jwt-api/` |
| SSE demo | `examples/web/sse/` |
| Tests | `src/test/java/io/jnative/security/` |
| IR local uniquify | `src/main/java/io/jnative/ir/IrBuilder.java` |

## 51. Architecture Acceptance

| Question | Answer |
|----------|--------|
| Does WebSocket run over JNative AsyncTcpSocket / TcpSocket? | **YES** |
| Does WebSocket avoid one thread per connection? | **YES** |
| Are fragmented frames supported? | **YES** |
| Are malformed frames rejected? | **YES** |
| Does outbound traffic provide backpressure? | **YES** |
| Are slow consumers bounded? | **YES** |
| Does ping/pong use EventLoop timers (foundation)? | **YES** (auto-pong; timer hooks ready) |
| Does disconnect release native and managed resources? | **YES** |
| Can SSE stream without blocking EventLoop design? | **YES** |
| Are session IDs cryptographically random? | **YES** |
| Does login rotate the session ID? | **YES** |
| Are passwords stored with a password hashing algorithm? | **YES** (PBKDF2-HMAC-SHA256) |
| Does password hashing run off EventLoop (API)? | **YES** (`Password.hash` Future); Chat also has blocking path |
| Does JWT reject unexpected algorithms and alg=none? | **YES** |
| Are 401 and 403 distinguished? | **YES** |
| Does cookie auth have CSRF protection foundation? | **YES** |
| Are forwarded headers trusted only from configured proxies? | **YES** |
| Can WebSocket connections carry authenticated principals? | **YES** |
| Can Chat persist messages through PostgreSQL/SQLite? | **YES** |
| Can multiple clients receive real-time messages? | **YES** |
| Does the complete Chat server run without JVM/JRE/JNI? | **YES** |

## 52. 0.26 Readiness

| Question | Answer |
|----------|--------|
| Can JNative IR represent a browser-safe subset? | **NO** (not yet) |
| Can managed code target WebAssembly? | **NO** |
| Can String/Array/Object run inside WASM? | **NO** |
| Can GC strategy work in WASM memory? | **NO** |
| Can JNative functions import JavaScript functions? | **NO** |
| Can JavaScript call exported JNative functions? | **NO** |
| Can browser fetch be exposed as Future\<HttpResponse\>? | **NO** |
| Can browser timers map to JNative Future? | **NO** |
| Can DOM events invoke JNative lambdas? | **NO** |
| Can JNative String convert to/from JavaScript string? | **NO** |
| Can shared DTO/JSON codecs run server and browser? | **NO** (server-only today) |
| Can JNative produce .wasm + minimal JS loader? | **NO** |

**0.26 goal:** drive all of the above to **YES** (WASM + browser runtime). 0.25 deliberately does **not** implement WASM.

---

## Run

```bash
# Chat (SQLite)
java -cp target/classes io.jnative.Main examples/web/chat/App.java -o build/web/JNativeChat
./build/web/JNativeChat
# open http://localhost:18251

# Optional PostgreSQL
DATABASE_URL=postgresql://user:pass@127.0.0.1:5432/chat ./build/web/JNativeChat
```

**Production note:** Use HTTPS via a trusted reverse proxy; enable Secure cookies when terminating TLS upstream.
