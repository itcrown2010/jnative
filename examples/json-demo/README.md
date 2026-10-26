# json-demo

Demonstrates `JsonObject`, `Json.stringify`, `Json.parse` (string fields).

```bash
java -jar ../../target/jnative.jar JsonDemo.java -o ../../build/json-demo
../../build/json-demo
```

Expected:

```text
{"hello":"jnative","n":"42"}
true
```

**Known gap:** `JsonObject.putNumber` / `JsonNumber` stringify may emit `0` on the current toolchain—tracked in the capability audit. Prefer string fields or verify numbers before relying on them.
