# http-server

**What this demonstrates:** stdlib `HttpServer` accept → read → respond → close.

## Prerequisites

`target/jnative.jar`, `clang`, free port **18081**.

## Build

```bash
java -jar ../../target/jnative.jar HttpHelloServer.java -o HttpHello
```

## Run

```bash
./HttpHello
# other terminal:
curl -s http://127.0.0.1:18081/
```

## Expected output

```text
Hello JNative
```

Server exits after one connection (demo).

## Notes

HTTP is stdlib on TCP — see `docs/en/sdk/http.md`. Same source also lives at `examples/HttpHelloServer.java`.
