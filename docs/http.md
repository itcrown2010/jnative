# HTTP (0.8.3 / 0.9)

作者：张晓达

With 0.9 threads, an HTTP server can `accept` then handle each `HttpConnection` on a worker
`Thread` subclass. Stdlib HTTP itself is unchanged (still TcpSocket-based).

## Principle

HTTP is a **JNative stdlib** above `TcpSocket`. It is **not** a compiler/runtime intrinsic.

```text
Application
    ↓
HttpClient / HttpServer  (stdlib/http/HttpLib.java)
    ↓
TcpSocket / TcpServer
    ↓
Network Runtime → OS TCP/IP
```

When user source mentions `HttpClient` / `HttpServer` / …, the compiler prepends
`stdlib/http/HttpLib.java` into the compilation unit.

## Client

```java
HttpResponse response = HttpClient.get("http://example.com/");
HttpRequest request = HttpRequest.create("POST", "http://127.0.0.1:8080/api");
request.header("Content-Type", "application/json");
request.bodyString("{\"name\":\"JNative\"}");
HttpResponse response = HttpClient.send(request);
```

Methods: GET / POST / PUT / DELETE / HEAD (library strings, not language enums).

## Server

```java
HttpServer server = HttpServer.listen("127.0.0.1", 8080);
HttpConnection connection = server.accept();
HttpServerRequest request = connection.readRequest();
connection.send(HttpResponse.textStatus(200, "Hello JNative"));
connection.close();
server.close();
```

No lambdas / interfaces / threads required.

## Response helpers

| API | Meaning |
|-----|---------|
| `HttpResponse.text(body)` | 200 + `text/plain; charset=utf-8` |
| `HttpResponse.textStatus(code, body)` | status + text body |
| `HttpResponse.bytesStatus(code, body)` | octet-stream |
| `HttpResponse.json(body)` | `application/json` (no serializer) |

(Method overloading is not available yet, so status+body uses `textStatus`.)

## Framing

- Request: `Connection: close` by default; `Content-Length` from **byte** length
- Response: `Content-Length`, `Transfer-Encoding: chunked` (extensions ignored; trailers consumed), or connection close
- No body for HEAD / 1xx / 204 / 304
- Limits: headers 64 KiB, body 32 MiB

## Redirects

Follows 301 / 302 / 303 / 307 / 308 up to 5 hops.

## Exceptions

```text
IOException
└── HttpException
    ├── HttpProtocolException
    ├── InvalidUrlException
    └── UnsupportedSchemeException
```

`https://` → `UnsupportedSchemeException` (TLS later).

Socket errors (`ConnectException`, …) propagate as `IOException`.

## Encoding

`bodyString()` / request `bodyString` use UTF-8 (same as `String` / `File`).
Binary bodies use `byte[]`.

## Blocking model

All HTTP I/O is blocking on `TcpSocket`. Keep-alive pooling is future work;
architecture allows a future `HttpTransport` / TLS socket without rewriting the parser.

## Streaming roadmap

Public API is buffered (`bodyBytes` / `bodyString`). Internal reads already chunk from
the socket; a future `HttpBodyReader` can stream to `File` without holding the full body.

## Examples

- `examples/HttpDownload.java` — GET → `File.writeBytes`
- `examples/HttpHelloServer.java` — one-shot server for curl

## Related general-purpose additions (not HTTP magic)

- `String`: `indexOf`, `substring`, `startsWith`/`endsWith`, `equalsIgnoreCase`, `trim`, `getBytes`, `fromBytes`, `valueOf(int)`, `toLowerCase`
- `Bytes`: `fromString` / `toString` / `concat` / `copy` / `slice` / `indexOf2`
- Array fields and `T[]` method returns
