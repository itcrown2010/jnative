# HTTP

**Status:** Implemented as **stdlib** on TCP (not a compiler intrinsic)

## Concept

`HttpClient` / `HttpServer` live in `stdlib/http/HttpLib.java`.

## Server example (verified)

`examples/HttpHelloServer.java` / `examples/http-server/`:

```java
HttpServer server = HttpServer.listen("127.0.0.1", 18081);
HttpConnection connection = server.accept();
HttpServerRequest request = connection.readRequest();
connection.send(HttpResponse.textStatus(200, "Hello JNative"));
connection.close();
server.close();
```

## Client

```java
HttpResponse response = HttpClient.get("http://127.0.0.1:18081/");
```

Helpers: `HttpResponse.text`, `textStatus`, `json` (content-type only—no automatic serializer).

Full contract: [`../../http.md`](../../http.md).

## Higher-level WebApp

`examples/web/hello/App.java` uses `jnative.web.WebApp` routing (experimental). See [../web/overview.md](../web/overview.md).
