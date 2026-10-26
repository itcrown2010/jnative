# Template: HTTP server (Experimental)

One-shot HTTP Hello on 127.0.0.1:18081 (same pattern as `examples/HttpHelloServer.java`).

```bash
java -jar ../../../target/jnative.jar src/Main.jn -o ../../../build/tpl-server
../../../build/tpl-server &
curl -s http://127.0.0.1:18081/
```
