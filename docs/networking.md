# Networking (0.8.2 / 0.9)

Blocking socket calls use `jn_enter_native` / `jn_leave_native` so STW GC is not blocked by
`accept`/`read`/`write`. Concurrent servers: spawn a `Thread` subclass per client (0.9).

## API

Compiler builtins (future package `jnative.net`):

```java
TcpSocket.connect(host, port)
TcpSocket.connectTimeout(host, port, timeoutMs)

socket.read(byte[] buffer)      // bytes read; 0 = EOF
socket.readBytes(maxBytes)
socket.readAllBytes()
socket.readString(maxBytes)
socket.readAllString()
socket.write(byte[] data)       // write-all
socket.writeString(text)        // UTF-8 write-all
socket.close()                  // idempotent
socket.isClosed()
socket.setReadTimeout(ms)
socket.setWriteTimeout(ms)

TcpServer.listen(host, port)
TcpServer.listenAny(port)       // bind 0.0.0.0
server.accept()                 // → TcpSocket
server.close()
server.isClosed()
```

Public API does **not** expose native socket handles (`fd` / `SOCKET`).

## Architecture

```text
JNative Application
        ↓
TcpSocket / TcpServer  (builtins)
        ↓
NetworkRuntimeEmitter  (+ SocketRuntimeEmitter / DnsRuntimeEmitter notes)
        ↓
PlatformSocket C layer (jnative_plat_tcp_*)
        ↓
OS TCP/IP stack
```

Compiler / SemanticAnalyzer do **not** emit `socket()` / `connect()` / `recv()` directly.
Platform differences (POSIX vs Winsock) stay in the C PlatformSocket layer.

Internal handle type: `NativeSocketHandle` (opaque; stored as `i64` in the object today).
Do not assume `handle == int` at the language layer.

## Blocking model

`connect` / `accept` / `read` / `write` are **blocking**.

Future: non-blocking sockets, event loops, async I/O (not in 0.8.2).

Single-threaded GC: during a blocking native network call, GC does not run.
After threads exist, safepoint / native-state coordination will be required.

## TCP byte-stream semantics

- `write` / `writeString` loop until all bytes are sent (partial `send` handled).
- `read` / `readBytes` return whatever `recv` delivered (1..N); never assume one message.
- `recv == 0` is EOF (`read` returns 0; `readAll*` stops).
- Binary `byte[]` is the core API; String helpers are UTF-8 convenience.

### UTF-8

`writeString` / `readString` / `readAllString` use UTF-8, matching `File` / `String`.
Invalid UTF-8 on read is currently copied as raw bytes into a String payload
(replacement/exception policy may tighten later; behavior is “passthrough bytes”).

## DNS

Hostnames resolve via `getaddrinfo` (`AF_UNSPEC`). Connect tries the full address list
(IPv4 and IPv6). Failure → `UnknownHostException`.

## Timeouts

`setReadTimeout` / `setWriteTimeout` / `connectTimeout` use `SO_RCVTIMEO` /
`SO_SNDTIMEO` / non-blocking connect + `poll`. Expiry → `SocketTimeoutException`.

## Resource ownership

Socket handles are **OS resources**. Call `close()`. Double-close is safe.
0.8.2 does not install a finalizer. Use-after-close → `SocketException`.

Servers set `SO_REUSEADDR` by default.

## Exceptions

```text
Exception
└── IOException
    ├── FileNotFoundException
    └── SocketException
        ├── ConnectException
        ├── UnknownHostException
        └── SocketTimeoutException

RuntimeException
└── IllegalArgumentException   // e.g. port out of range at runtime
```

Native `errno` / Winsock errors are converted at the runtime boundary; they do not leak.

## SIGPIPE / EINTR

POSIX writes use `MSG_NOSIGNAL` / `SO_NOSIGPIPE` so a closed peer becomes
`SocketException`, not process death. Interrupted syscalls retry on `EINTR`.

## GC

Result buffers are GC-managed `byte[]` / `String`. Temporary recv buffers use `malloc`/`free`.
`JNATIVE_GC_STRESS=1` is covered by networking E2E tests.

## Platform

| Platform | Status |
|----------|--------|
| macOS / Linux POSIX | Implemented (E2E) |
| Windows Winsock | Architecture stub (`WSAStartup` reserved); not CI-tested yet |

## Examples

- `examples/RawHttp.java` — raw HTTP/1.0 over `TcpSocket` (needs network)
- `examples/EchoServer.java` — single-threaded blocking echo server

## HTTP

HTTP/1.1 is implemented in JNative stdlib on top of this layer (0.8.3). See [http.md](http.md).

## Limitations

No HTTPS/TLS, UDP, WebSocket, async I/O, `epoll`/`kqueue`/`IOCP`, or threads.
`TcpServer` is single-threaded blocking only.
