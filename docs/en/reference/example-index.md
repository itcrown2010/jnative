# Example Index

| Example | Features | Buildable | Documented |
|---|---|---:|---:|
| `examples/Hello.java` | basic syntax | Yes | Yes |
| `examples/hello-world/` | project hello | Yes | Yes |
| `examples/file-io/` | filesystem | Yes | Yes |
| `examples/notes-cli/` | CLI + files | Yes | Yes |
| `examples/json-demo/` | JSON strings | Yes | Yes |
| `examples/tcp-echo/` | TCP server | Yes | Yes |
| `examples/EchoServer.java` | TCP echo | Yes | Yes |
| `examples/http-server/` | HTTP server | Yes | Yes |
| `examples/HttpHelloServer.java` | HTTP server | Yes | Yes |
| `examples/HttpDownload.java` | HTTP client | Yes | Partial |
| `examples/multithreading/` | Thread + Mutex | Yes | Yes |
| `examples/LambdaClosure.java` | lambdas | Yes | Yes |
| `examples/NativeCallback.java` | FFI | Yes | Yes |
| `examples/web/hello` | WebApp | Yes | Yes |
| `examples/guide-tasks/` | web/mobile shell | Yes | Yes |
| `examples/desktop-sample/` | desktop UI | Partial | Partial |
| `examples/sudoku/` | multi-target sample | Partial | Yes |
| `examples/webui/` | WebUI | Often broken | Partial |

## Missing / thin examples (priority)

| Gap | Action taken / needed |
|-----|------------------------|
| Threads | **Added** `examples/multithreading/` |
| JSON | **Added** `examples/json-demo/` (numbers gap noted) |
| TCP project README | **Added** `examples/tcp-echo/` |
| Notes app tutorial | **Added** `examples/notes-cli/` + tutorial |
| HTTPS client sample README | still thin — use e2e + HttpDownload |
| Generics-focused mini project | language demos exist; dedicated README optional |
| Inheritance showcase README | `Polymorphism.java` present; wrap README later |

Smoke script: `scripts/docs/verify-en-examples.sh` (extend when wiring new samples).
