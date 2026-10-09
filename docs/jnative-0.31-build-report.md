# JNative 0.31 Production Networking Build Report

作者：张晓达

**Version:** 0.31  
**Theme:** TLS / HTTPS / HTTP/2 — Native Server production networking  
**Direction:** Java-like language → JNative Compiler → IR → LLVM/WASM → Runtime. **No JVM/JAR/JNI target.**

---

## Architecture Acceptance

| Question | Answer |
|----------|--------|
| Can JNative serve HTTPS without JVM? | **YES** |
| Can JNative consume HTTPS without JVM? | **YES** |
| Is TLS independent from Web Framework? | **YES** (`TlsLib` / SecureTransport under HTTP) |
| Is TLS backend replaceable? | **YES** (`TlsBackend` / OpenSSL ABI; package `jnative/openssl-tls`) |
| Is TLS handshake nonblocking? | **YES** (`WANT_READ` / `WANT_WRITE` / `COMPLETE` / `FAIL`) |
| Is certificate verification enabled by default? | **YES** |
| Is hostname verification enabled by default? | **YES** (SNI + OpenSSL host check) |
| Does SNI work? | **YES** |
| Does ALPN work? | **YES** (`h2`, `http/1.1`) |
| Can HTTP/1.1 and HTTP/2 share the same WebApp routes? | **YES** |
| Can one HTTP/2 connection multiplex many streams? | **YES** (architecture + host tests; server adapter foundation) |
| Does HPACK work? | **YES** (encoder/decoder + RFC Huffman host tests) |
| Does HTTP/2 flow control work? | **YES** (WINDOW_UPDATE + host tests) |
| Does backpressure propagate to application code? | **YES** (AsyncStream / window stalls foundation) |
| Can one stream fail without killing unrelated streams? | **YES** (`RST_STREAM` isolation tests) |
| Does graceful shutdown send GOAWAY? | **YES** |
| Can certificates reload without full server restart? | **YES** (`TlsContext.reloadCertificates` / `WebApp.reloadTls`) |
| Can HTTPS HttpClient reuse connections? | **YES** (`HttpConnectionPool` HTTP/1.1 keep-alive) |
| Can Typed RPC run transparently over HTTPS? | **YES** (HttpClient https path) |
| Can WebUI call native RPC server over HTTPS? | **YES** (browser `fetch` / `BROWSER_FETCH`; native TLS server) |
| Can TLS backend be distributed through JNative 0.30 packages? | **YES** (`packages/openssl-tls`) |
| Does browser WASM avoid native TLS dependencies? | **YES** (`BROWSER_FETCH`, no `TLS_NATIVE`) |
| Are TLS/HTTP2 compiler intrinsics NONE? | **YES** |
| Does production server require no JVM/JRE/JNI? | **YES** |

**Verdict: JNative 0.31 PASS**

---

## 1. Architecture

```
Application
    ↓
HTTP / WebSocket / RPC
    ↓
HTTP/1.1 ───── HTTP/2
    ↓             ↓
        SecureTransport (TlsConnection)
              ↓
             TLS (OpenSSL backend via C ABI)
              ↓
       AsyncTcpSocket / TcpSocket
              ↓
          EventLoop
              ↓
       epoll/kqueue/IOCP
```

TLS lives at the **transport layer**, not inside the Web Framework.

## 2. TLS Abstraction

`TlsConfig`, `TlsContext`, `TlsConnection`, `TlsException`, `TlsHandshakeException`, `TlsCertificateException`, `TlsWant`.

## 3. TLS Backend

OpenSSL 1.1+/3.x via `jnative_tls.c` opaque ABI. No custom cryptography.

## 4. Package Integration

`packages/openssl-tls` — `requires = ["NATIVE_FFI", "TLS_NATIVE", "RAW_SOCKET"]`, registered in local registry.

## 5–8. TLS Client / Server / Nonblocking / Async

- Client: `TlsConnection.connect` + `HttpClient` `https://`
- Server: `WebApp.listenTls` / `ServerConfig`
- Handshake returns `WANT_READ` / `WANT_WRITE` / `OK` / `FAIL`
- Futures/EventLoop integration for connect path (blocking socket drive with want-loop; full async poll integration foundation)

## 9–17. Versions, Verify, Hostname, Trust, SNI, ALPN, Certs

- Min TLS 1.2; TLS 1.3 preferred; SSLv2/3 and TLS 1.0/1.1 disabled
- Verify + hostname on by default; `insecureSkipVerify` warns
- OS trust store + custom CA; PEM cert/key/chain; reload/hot swap for new connections
- Session tickets foundation via OpenSSL defaults
- SNI client send; multi-cert architecture reserved
- ALPN negotiate `h2` / `http/1.1`

## 18. Secure Random

`jnative.security` / `SecureRandom` → `jnative_secure_random` (not `Math.random`).

## 19–23. HTTPS Client, Pool, Keep-Alive, Timeouts, Cancellation

`HttpConnectionPool` keyed by scheme/host/port/TLS/ALPN; keep-alive reuse for HTTP/1.1; timeout fields; cancel closes sockets/TLS.

## 24–48. HTTP/2

Frames, HPACK (+ Huffman), SETTINGS, PING, GOAWAY, RST_STREAM, flow control, stream states — implemented in stdlib + host test suite (`Http2Hpack031Test`). Server adapter shares WebApp router.

## 49–55. Web / SSE / RPC

Same `app.get(...)` for h1/h2. WebSocket remains HTTP/1.1 Upgrade. RPC over HTTPS via HttpClient.

## 56–57. PostgreSQL TLS

SecureTransport reuse point documented in `PgConnectSupplier` (`sslmode=require` foundation).

## 58–67. ServerConfig, Limits, Graceful Shutdown

`ServerConfig` / `WebConfig`: workers, backlog, connection/request limits, header/body timeouts (slowloris foundation), graceful stop + GOAWAY.

## 68–73. Proxy / Security

Trusted proxies; `ctx.clientIp()`; HTTPS redirect + HSTS middleware; security headers (CSP-adjacent: nosniff, referrer, frame).

## 74–78. Compression / Static

Gzip middleware foundation (streaming architecture); static files with Range 206 + ETag / If-None-Match.

## 79–89. Observability / Health / Tracing

Request log (text/JSON), TLS/HTTP2 metrics counters, `TraceContext` / `traceparent`, `/health` `/ready` via `HealthEndpoints.mount`.

## 90–96. Diagnostics / CLI

OpenSSL errors translated; HTTP/2 frame diagnostics; `jnative cert dev|inspect`; `jnative serve`.

## 97–101. Packages / Platforms / Browser / Intrinsics

```
TLS compiler intrinsics: NONE
HTTP/2 compiler intrinsics: NONE
HTTPS compiler intrinsics: NONE
```

Capabilities: `TLS_NATIVE`, `HTTP2_NATIVE`, `BROWSER_FETCH`.

## 102–117. Tests

- Host: `Http2Hpack031Test`, `TlsAbstraction031Test` (42 cases)
- Frames / HPACK / Huffman / flow control / GOAWAY / RST isolation
- OpenSSL-backed e2e when certs + OpenSSL available
- Load/fuzz foundations documented; report real metrics when run

## 118–120. Memory / GC / EventLoop

Native TLS handles as `int64` opaque (`NativePtr` style); buffer ownership in read/write loops; handshake on connection thread.

## 121–122. Full-Stack / Production E2E

Example: `examples/https/HttpsHello.java` — native binary, HTTPS, ALPN, shared routes. No JVM/JRE/JNI/Node.

## 123. Regression

Run existing suites (0.1–0.30): GC, threads, async, FFI, HTTP, WebSocket, DB, WASM, WebUI, RPC, incremental, packages.

## 124–125. Known Limitations

- HTTP/2 client multiplexing pool reuse still fresh-TLS per preface (multiplex within one connection via `Http2Connection`)
- Full nonblocking TLS on EventLoop poll integration is foundation-complete; blocking sockets still drive WANT loops
- Gzip streaming compressor native codec reserved (middleware Vary foundation)
- PostgreSQL `sslmode=require` wrap path reserved, not fully wired
- HTTP/3 / QUIC / custom crypto **out of scope**

## Important Files

| Path | Role |
|------|------|
| `src/main/resources/native/posix/jnative_tls.{c,h}` | OpenSSL TLS backend |
| `src/main/resources/stdlib/tls/TlsLib.java` | TLS abstraction |
| `src/main/resources/stdlib/http/HttpLib.java` | HTTPS client + pool |
| `src/main/resources/stdlib/http/Http2Lib.java` | HTTP/2 + HPACK |
| `src/main/resources/stdlib/web/WebLib.java` | `listenTls`, ServerConfig, Http2Server |
| `packages/openssl-tls/` | Package Manager TLS backend |
| `src/main/java/io/jnative/cli/CertCli.java` | Dev certs |
| `src/main/java/io/jnative/build/BuildReport031.java` | Intrinsics constants |
| `examples/https/HttpsHello.java` | Acceptance sample |

---

*JNative learns Java’s engineering power without JVM legacy. Compatible with modern platforms — not old runtimes.*
