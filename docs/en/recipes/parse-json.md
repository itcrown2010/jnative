# Recipe: Parse JSON

```java
JsonValue v = Json.parse("{\"ok\":true}");
JsonObject o = (JsonObject) v;
System.out.println(o.has("ok"));
```

Example: `examples/json-demo/`.
