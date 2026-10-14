# Recipe: TCP server (echo)

```java
TcpServer server = TcpServer.listen("127.0.0.1", 19090);
TcpSocket client = server.accept();
byte[] data = client.readBytes(1024);
client.write(data);
client.close();
```

Example: `examples/tcp-echo/`, `examples/EchoServer.java`.
