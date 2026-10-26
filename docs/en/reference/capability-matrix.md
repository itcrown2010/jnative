# Capability Matrix

**Audit date:** 2026-10-13  
**CLI version:** `JNative 0.32` (`java -jar target/jnative.jar --version`)  
**Evidence host:** macOS arm64 (darwin) unless noted  
**Rule:** *Implemented* requires compiler/runtime code **and** tests or a verified runnable example. Class names, TODOs, and roadmap text alone do **not** count.

Legend for Yes/Partial/No columns:

| Column | Meaning |
|--------|---------|
| Implemented | Code path exists and is exercised |
| Tested | Automated e2e/unit under `src/test/java/io/jnative/e2e/` (or equivalent) |
| Example | Runnable sample under `examples/` with documented how-to |
| Documented | English page under `docs/en/` that matches reality |
| Stability | Stable / Experimental / Partial / Planned / Not Supported |

## Language

| Category | Feature | Implemented | Tested | Example | Documented | Stability | Notes |
|---|---|---:|---:|---:|---:|---|---|
| Language | Variables / locals | Yes | Yes | Yes | Yes | Stable | `PrimitiveOps` / Hello |
| Language | Functions / methods | Yes | Yes | Yes | Yes | Stable | |
| Language | Classes / fields / ctors | Yes | Yes | Yes | Yes | Stable | `PersonBasic`, classes guides |
| Language | Inheritance / override | Yes | Yes | Yes | Yes | Stable | `Polymorphism`, `InheritedFields` |
| Language | Interfaces | Yes | Yes | Partial | Yes | Experimental | `InterfaceEndToEndTest` |
| Language | Generics | Yes | Yes | Yes | Yes | Experimental | erasure-style; `GenericsEndToEndTest` |
| Language | Lambda / closures | Yes | Yes | Yes | Yes | Experimental | `LambdaClosure.java`; wasm risk |
| Language | Method references | Yes | Yes | Partial | Yes | Experimental | language guide |
| Language | Exceptions try/catch/finally | Yes | Yes | Partial | Yes | Experimental | `ExceptionEndToEndTest` |
| Language | Arrays | Yes | Yes | Yes | Yes | Stable | |
| Language | Strings | Yes | Yes | Yes | Yes | Stable | `StringEndToEndTest` |
| Language | Operators / control flow | Yes | Yes | Yes | Yes | Stable | |
| Language | switch / enum | Yes | Yes | Partial | Partial | Experimental | `SwitchEnumEndToEndTest` |
| Language | foreach / Iterable | Yes | Yes | Yes | Yes | Experimental | `FinalDaForeachDemo` |
| Language | Annotations (user) | Partial | Partial | No | Partial | Experimental | limited; not Java SE |
| Language | Package / import | Yes | Yes | Yes | Yes | Experimental | multipackage examples |
| Language | Native / FFI decls | Yes | Yes | Yes | Yes | Experimental | `NativeCallback.java` |
| Language | async / await | Yes | Yes | Partial | Partial | Experimental | `Async*EndToEndTest` |
| Language | Reflection | No | No | No | Yes | Not Supported | product position |
| Language | Full Java SE | No | No | No | Yes | Not Supported | |

## Runtime

| Category | Feature | Implemented | Tested | Example | Documented | Stability | Notes |
|---|---|---:|---:|---:|---:|---|---|
| Runtime | GC (GC2) | Yes | Yes | Partial | Yes | Experimental | `Gc*EndToEndTest`; wasm single-thread |
| Runtime | Threads (`extends Thread`) | Yes | Yes | Yes | Yes | Experimental | native only; `ThreadEndToEndTest` |
| Runtime | Synchronization (`Mutex`, atomics) | Yes | Yes | Yes | Yes | Experimental | Mutex + AtomicInt in thread e2e |
| Runtime | Thread pools / concurrency library (`ExecutorService`, `TaskFuture`, `CountDownLatch`, `Semaphore`, `ReentrantLock`, `CyclicBarrier`, `BlockingQueue`) | Yes | Yes | Yes | No (rejected by capability checker) | Experimental | native only; `ConcurrentEndToEndTest` |
| Runtime | No JVM at runtime | Yes | Yes | Yes | Yes | Stable | AOT native / wasm |

## SDK

| Category | Feature | Implemented | Tested | Example | Documented | Stability | Notes |
|---|---|---:|---:|---:|---:|---|---|
| SDK | Console (`System.out`) | Yes | Yes | Yes | Yes | Stable | |
| SDK | File I/O (`File.*`) | Yes | Yes | Yes | Yes | Experimental | builtins + `IoLib`; Windows incomplete |
| SDK | Streams (`IoLib` / StreamLib) | Yes | Yes | Partial | Partial | Experimental | `IoStreamEndToEndTest` |
| SDK | TCP (`TcpSocket` / `TcpServer`) | Yes | Yes | Yes | Yes | Experimental | `NetworkEndToEndTest`, EchoServer |
| SDK | UDP / DNS | Yes | Yes | Partial | Partial | Experimental | `DnsUdpEndToEndTest` |
| SDK | HTTP client/server | Yes | Yes | Yes | Yes | Experimental | `HttpLib`; `HttpEndToEndTest` |
| SDK | HTTPS / TLS | Yes | Yes | Partial | Partial | Experimental | `HttpsNative` / `TlsNative` e2e |
| SDK | JSON (`Json` / `JsonObject`) | Yes | Partial | Yes | Yes | Experimental | `JsonLib`; Studio e2e uses objects |
| SDK | Collections | Yes | Yes | Partial | Partial | Experimental | `CollectionsEndToEndTest` |
| SDK | Date/Time | Yes | Yes | Partial | Partial | Experimental | `TimeEndToEndTest` |
| SDK | Math | Yes | Yes | Partial | Partial | Experimental | `MathEndToEndTest` |
| SDK | FFI | Yes | Yes | Yes | Yes | Experimental | `FfiEndToEndTest` |
| SDK | SQLite / DB | Yes | Partial | Partial | Partial | Experimental | blockers for prod auth/SCRAM |
| SDK | Desktop UI | Yes | Partial | Yes | Partial | Experimental | macOS samples; Win/Linux unverified |
| SDK | WebApp routing | Yes | Partial | Yes | Yes | Experimental | `WebLib` + `examples/web/hello` |
| SDK | WebUI Html components | Partial | No | Broken | Partial | Experimental | webui counter historically fails WAT |

## Tools

| Category | Feature | Implemented | Tested | Example | Documented | Stability | Notes |
|---|---|---:|---:|---:|---:|---|---|
| Tools | CLI compile / build / new / doctor | Yes | Yes | Yes | Yes | Stable | doctor verified macOS arm64 |
| Tools | `jnative test` | Yes | Partial | Partial | Partial | Experimental | |
| Tools | `jnative lsp` / analyze | Yes | Partial | — | Yes | Experimental | VS Code integration |
| Tools | Package manager (add/publish/…) | Yes | Partial | Partial | Partial | Experimental | commands present; E2E sparse |
| IDE | VS Code extension 0.8.x | Yes | Partial | Yes | Yes | Experimental | syntax + build/run/LSP/debug MVP |
| IDE | Formatter | Yes | Partial | — | Yes | Experimental | document format; no range format |

## Platforms

| Category | Feature | Implemented | Tested | Example | Documented | Stability | Notes |
|---|---|---:|---:|---:|---:|---|---|
| Platform | macOS arm64 native | Yes | Yes | Yes | Yes | Stable | primary verified host |
| Platform | macOS x86_64 | Yes | No | No | Partial | Unverified | same toolchain; not exercised here |
| Platform | Linux native | Yes | No | No | Partial | Unverified | no host evidence in this audit |
| Platform | Windows native | Partial | No | No | Partial | Unverified / Partial | FS gaps; no Windows host evidence |
| Platform | WebAssembly (wasm32-browser) | Yes | Yes | Yes | Yes | Experimental | guide-tasks / sudoku |
| Platform | Android (WebView + wasm) | Yes | Partial | Yes | Yes | Experimental | assembleDebug; not full native UI |
| Platform | iOS (WKWebView + wasm) | Yes | Partial | Yes | Yes | Experimental | Simulator; not full native UI |
| Platform | Native mobile UI SDK | No | No | No | Yes | Not Supported | FFI alone ≠ mobile apps |

## Related matrices

- [Platform matrix](platform-matrix.md)
- [Web capability matrix](../web/capability-matrix.md)
- [Desktop capability matrix](../desktop/capability-matrix.md)
- [Mobile capability matrix](../mobile/capability-matrix.md)
- [Example index](example-index.md)
- Developer Chinese inventory: [`../../developer/FACT_INVENTORY.md`](../../developer/FACT_INVENTORY.md)

## Changelog for this matrix

Update this table when adding a user-facing feature **after** tests + example + docs land (see [versions.md](../versions.md) and CONTRIBUTING).
