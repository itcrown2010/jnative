# Start an HTTP server

## Code
```java
HttpServer server = HttpServer.listen("127.0.0.1", 18081);
HttpConnection c = server.accept();
HttpServerRequest req = c.readRequest();
c.send(HttpResponse.textStatus(200, "Hello JNative"));
c.close();
server.close();
```

## Related
`examples/http-server/` · [../web/http-server.md](../web/http-server.md)
