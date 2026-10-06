# JNative

**JNative** is an experimental ahead-of-time native compiler for a **subset** of the Java language.

```text
Java Source
  → Lexer → Parser → AST → Semantic → JNative IR → LLVM IR → clang
  → Native Executable
```

Generated programs do **not** require a JVM/JRE at runtime.

> **Important:** JNative is **not** a complete Java SE implementation.
> Current release: **0.9 — Multithreading & Concurrent Runtime**.

## Requirements

- Java 21+
- Maven 3.9+
- `clang` on `PATH`

## Build

```bash
mvn package
mvn test
```

## Use

```bash
java -jar target/jnative.jar examples/Hello.java -o Hello
./Hello

java -jar target/jnative.jar examples/Cat.java -o jcat
./jcat hello.txt
```

## What's new in 0.8.3

- HTTP/1.1 `HttpClient` / `HttpServer` as JNative stdlib on `TcpSocket`
- Chunked transfer, redirects, binary bodies, curl E2E
- `examples/HttpDownload.java`, `examples/HttpHelloServer.java`

## Documentation

- [HTTP](docs/http.md)
- [Networking](docs/networking.md)
- [File I/O](docs/file-io.md)
- [Architecture](docs/architecture.md)
- [Language support](docs/language-support.md)
- [FFI](docs/ffi.md)
- [Native struct](docs/native-struct.md)
- [GC](docs/gc.md)
- [Exceptions](docs/exceptions.md)

## License

MIT — see [LICENSE](LICENSE).
