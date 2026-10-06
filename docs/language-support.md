# JNative Language Support

JNative implements a **subset** of Java. It is **not** a Java SE implementation.

## Supported

### From 0.1–0.6

Primitives, control flow, classes, inheritance, vtable, constructors, arrays, String, RTTI, precise GC, `System.gc()`, exceptions.

### From 0.7

- `@Native` / `static native` methods (C ABI / FFI)
- `NativePtr`, `NativeMemory`
- String → temporary CString for libc calls
- Linker `-l` from `@Native(library=…)`

### From 0.8

- `@NativeStruct` + `NativeStructLayout` (ABI layout, no GC header)
- Typed `NativePtr<T>` (compiler-recognized)
- `@Stack` stack allocation for native structs
- `Native.addressOf` / `Native.allocate<T>` / typed load-store
- Escape rejection for stack pointers
- `--emit-layout`, `--library-path`, `--link`

### New in 0.8.1

- Builtin `File` read/write/exists/size/delete (UTF-8 / binary)
- `IOException` / `FileNotFoundException`
- Real `main(String[] args)` from process argv
- Examples: `Cat.java`, `Copy.java`

### New in 0.8.2

- Builtin `TcpSocket` / `TcpServer` (blocking TCP client/server)
- DNS via hostname (`getaddrinfo`, IPv4+IPv6)
- `SocketException` / `ConnectException` / `UnknownHostException` / `SocketTimeoutException`
- `IllegalArgumentException` (e.g. invalid port at runtime)
- Examples: `RawHttp.java`, `EchoServer.java`

### New in 0.8.3

- HTTP/1.1 client + server as **stdlib** (not intrinsics)
- `String` helpers: indexOf, substring, startsWith/endsWith, equalsIgnoreCase, trim, getBytes, …
- `Bytes` helpers; array fields / `T[]` return types
- Examples: `HttpDownload.java`, `HttpHelloServer.java`

### New in 0.9

- Native OS threads (`extends Thread`, `start`/`join`/`sleep`/`currentThread`/`id`)
- Thread-local exception state + per-thread shadow stacks
- Multi-thread STW GC + safepoints (alloc / method entry / loop backedge)
- `Mutex`, `Condition`, `volatile`, `AtomicInt`/`AtomicLong`/`AtomicBool`
- Static fields (+ field initializers / clinit)

## Interfaces (0.14)

Supported: `interface` / `implements` / interface `extends`, abstract classes/methods, functional-interface lambdas, `instanceof`/cast to interfaces. See [interfaces.md](interfaces.md).

## Not Supported (yet)

- default interface methods, full generics, try-with-resources, multi-catch `A | B`
- Full stack traces / suppressed exceptions
- JNI, callbacks, function pointers, C++ ABI
- Struct-by-value ABI, union, bitfields, packed structs
- Managed stack objects, `@NoGC`
- Full standard library / `synchronized` / reflection / virtual threads
- HTTPS/TLS, UDP, WebSocket, async/event-loop networking
- Method overloading, `break`/`continue`, collections framework
- Concurrent/generational GC

## Encoding note

String payload is **UTF-8** (documented; runtime-isolated). Future: UTF-16.
