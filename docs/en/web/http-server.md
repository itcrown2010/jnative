# HTTP server

**Status:** Implemented (stdlib)

## Concept

Blocking accept loop over TCP; optional worker threads for connections.

## Example

```java
HttpServer server = HttpServer.listen("127.0.0.1", 18081);
HttpConnection c = server.accept();
HttpServerRequest req = c.readRequest();
c.send(HttpResponse.textStatus(200, "Hello JNative"));
c.close();
server.close();
```

Build: see `examples/http-server/README.md`.

## Limits

Header/body size limits, HTTP/1.1 framing—[`../../http.md`](../../http.md).
