# Make an HTTP request

## Goal
GET a URL on the native target.

## Code
```java
HttpResponse r = HttpClient.get("http://127.0.0.1:18081/");
System.out.println(r.statusCode());
System.out.println(r.bodyString());
```

## Related
`HttpRequest.create` + `HttpClient.send` · `examples/HttpDownload.java`
