# Recipe: HTTP GET

```java
HttpResponse r = HttpClient.get("http://127.0.0.1:18081/");
System.out.println(r.statusCode());
System.out.println(r.bodyString());
```

See `HttpResponse` in `HttpLib.java`. Example client: `examples/HttpDownload.java`.
