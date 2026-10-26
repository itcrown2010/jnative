# multithreading

Demonstrates `Thread` subclassing, `start`/`join`, and `Mutex`.

**Native only** (not wasm32-browser).

```bash
java -jar ../../target/jnative.jar Main.java -o ../../build/thread-demo
../../build/thread-demo
```

Expected output (order of `1`/`2` may vary):

```text
1
2
2000
```

or `2` then `1`, then `2000`.
