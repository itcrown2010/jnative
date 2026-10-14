# Recipe: HTTP POST

```java
HttpRequest req = HttpRequest.create("POST", "http://127.0.0.1:18081/echo");
req.header("Content-Type", "text/plain");
req.bodyString("hi");
HttpResponse r = HttpClient.send(req);
```

Server must accept the method; use `examples/http-server/` as a starting point.
