# Networking (TCP)

**Status:** Implemented on POSIX; Windows stubbed/incomplete

```text
TcpSocket / TcpServer  →  NetworkRuntimeEmitter  →  platform C
```

No public raw socket handles. Blocking I/O only at this layer. DNS via `getaddrinfo`.

Docs: [`../../networking.md`](../../networking.md). Example: `examples/EchoServer.java`.
