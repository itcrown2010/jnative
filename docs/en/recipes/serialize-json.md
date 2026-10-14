# Recipe: Serialize JSON

```java
JsonObject o = new JsonObject();
o.putString("name", "Ada");
o.putNumber("n", 1);
System.out.println(Json.stringify(o));
```

Example: `examples/json-demo/`.
