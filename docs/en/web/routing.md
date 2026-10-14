# Routing

**Status:** Experimental (`WebApp`)

`examples/web/hello/App.java`:

```java
WebApp app = new WebApp();
app.get("/", new RootHandler());
app.get("/hello/:name", new HelloHandler());
app.post("/echo", new EchoHandler());
app.listen(18080);
```

Handlers implement `WebHandler` and receive `WebContext` (`param`, `bodyString`, `text`, `jsonValue`).
