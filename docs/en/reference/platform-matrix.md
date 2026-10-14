# Platform & capability matrix (developer source of truth)

**Audit host:** macOS arm64 · **CLI:** `JNative 0.32`  
Every row links documentation, example, and tests when available.

| Capability | Status | Platforms | Documentation | Example | Tests |
|---|---|---|---|---|---|
| Classes | Stable | native (verified macOS) | [language/classes](../language/classes.md) | `PersonBasic.java` | EndToEnd / class demos |
| Inheritance | Stable | native | [inheritance](../language/inheritance.md) | `Polymorphism.java` | e2e demos |
| Interfaces | Experimental | native | [interfaces](../language/interfaces.md) | — | `InterfaceEndToEndTest` |
| Generics | Experimental | native / wasm* | [generics](../language/generics.md) | cli-todo ArrayList | `GenericsEndToEndTest` |
| Lambdas | Experimental | native; wasm risk | [lambdas](../language/lambdas.md) | `LambdaClosure.java` | Closure e2e |
| Exceptions | Experimental | native | [exceptions](../language/exceptions.md) | — | `ExceptionEndToEndTest` |
| File I/O | Experimental | native; **not** wasm | [sdk/filesystem](../sdk/filesystem.md) | [file-io](../../../examples/file-io/), [cli-todo](../../../examples/tutorials/cli-todo/) | `FileIoEndToEndTest` |
| Networking TCP | Experimental | native | [sdk/networking](../sdk/networking.md) | [tcp-echo](../../../examples/tcp-echo/) | `NetworkEndToEndTest` |
| HTTP | Experimental | native | [sdk/http](../sdk/http.md) | [http-server](../../../examples/http-server/) | `HttpEndToEndTest` |
| JSON | Experimental | native (+stdlib link) | [sdk/json](../sdk/json.md) | [json-demo](../../../examples/json-demo/) | Studio/Json usage; **number gap** |
| Concurrency | Experimental | native only | [sdk/concurrency](../sdk/concurrency.md) | [multithreading](../../../examples/multithreading/) | `ThreadEndToEndTest` |
| FFI | Experimental | native only | [sdk/ffi](../sdk/ffi.md) | `NativeCallback.java` | `FfiEndToEndTest` |
| Desktop GUI | Experimental | macOS samples; Win/Linux unverified | [desktop](../desktop/index.md) | `desktop-sample` | `UiEndToEndTest` |
| Web (native HTTP) | Experimental | native | [web](../web/index.md) | http-server / web/hello | Http* e2e |
| Web (wasm browser) | Experimental | browser | [web](../web/index.md) | guide-tasks | verify_web scripts |
| Mobile | Experimental container | Android/iOS WebView | [mobile/status](../mobile/status.md) | guide-tasks mobile | build scripts |
| VS Code | Experimental | editor host | [vscode](../vscode/index.md) | extension tests | `tools/vscode-jnative` |

\*wasm: language subset; no File/TCP/Threads/FFI.

Also see: [capability-matrix.md](capability-matrix.md) · developer [FACT_INVENTORY](../../developer/FACT_INVENTORY.md)
