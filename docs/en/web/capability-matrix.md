# Web Capability Matrix

**Important:** Having an HTTP client/server in the **native** stdlib does **not** make JNative a full web application framework. Browser apps compile to **wasm32-browser** and typically use a JS shell.

| Capability | Current Status | Evidence |
|---|---|---|
| HTTP Client (native) | Experimental | `HttpClient` in `HttpLib.java`; `HttpEndToEndTest` |
| HTTP Server (native) | Experimental | `HttpServer.listen`; `examples/http-server`, `HttpHelloServer.java` |
| HTTPS / TLS (native) | Experimental | `HttpsNativeEndToEndTest`, `TlsNativeEndToEndTest` |
| Routing (`WebApp`) | Experimental | `WebLib` `WebApp`; `examples/web/hello` |
| Request parsing | Experimental | `HttpServerRequest` in HttpLib |
| Response generation | Experimental | `HttpResponse.text/textStatus/json/fileStream` |
| JSON | Experimental | `JsonLib` (`Json.parse` / `Json.stringify`) |
| Static files | Partial | file streaming responses; not a full static site server product |
| Templates | Partial / Experimental | limited; see templates.md — do not assume Jinja-style engine |
| Middleware | Partial | app-level patterns only |
| Database | Experimental | SQLite paths exist; production auth/KDF blockers documented |
| Authentication | Experimental | Token-style session in guide-tasks; **not** production Cookie/session suite |
| WebSocket | Not Supported | no verified product API |
| Browser WASM core | Experimental | guide-tasks / sudoku `verify_web` |
| Browser DOM / JS glue | Experimental | JS shell drives UI |
| Browser `fetch` from WASM glue | Not Supported | use JS adapter |
| TLS termination in wasm | Not Supported | |
| Deployment / app store | Not Supported | no verified pipeline |

### What you can build today

1. **Native HTTP services** (CLI process listening on TCP) — best supported path.  
2. **Browser / mobile WebView apps** that keep business logic in wasm and UI/network in JS — see `examples/guide-tasks`.  
3. **Not yet:** a drop-in Spring/Express clone with middleware, WebSocket, and production auth.

See also: [overview](overview.md), [http-server](http-server.md), developer matrix [`../../developer/WEB_MOBILE_CAPABILITY_MATRIX.md`](../../developer/WEB_MOBILE_CAPABILITY_MATRIX.md).
