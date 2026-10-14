# Web project setup

## Native HTTP executable

```bash
java -jar target/jnative.jar new webdemo
# edit src to use HttpServer or WebApp
java -jar target/jnative.jar build --project webdemo
```

Or compile a single file: `examples/HttpHelloServer.java`.

## Wasm browser core

Use `jnative` with `--target wasm32-browser` as in guide-tasks scripts. Pair with a JS shell—see guide-tasks docs.
