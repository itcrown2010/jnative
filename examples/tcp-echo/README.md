# tcp-echo

Demonstrates `TcpServer` / `TcpSocket` echo.

## Build & run

```bash
java -jar ../../target/jnative.jar EchoMain.java -o ../../build/tcp-echo-demo
# terminal A:
../../build/tcp-echo-demo
# terminal B:
printf 'ping' | nc 127.0.0.1 19090
```

Expected server lines: `listening 19090` then `echoed`.
