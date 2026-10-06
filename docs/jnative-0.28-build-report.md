# JNative 0.28 Full-Stack Typed RPC Build Report

**Date:** 2026-10-07  
**Version:** 0.28  
**Constraint:** Compile-time RPC contracts — **no runtime reflection**, **no LLVM RPC intrinsics**.

---

## 1. RPC Architecture

```
@Rpc interface (shared source)
        ↓
RpcAnalyzer → RpcServiceModel / RpcMethodModel
        ↓
RpcCodeGenerator (Java source)
        ↓
ordinary JNative IR / native binary
        │
        ├── Server: generated WebHandler dispatchers (direct impl.method(...))
        ├── Native client: generated Xxx_RpcClient + RpcHttp
        └── Schema: rpc-schema.json + --emit-rpc report
```

## 2–7. @Rpc / Analyzer / Models / Method IDs / Schema

| Item | Status |
|------|--------|
| `@Rpc("/path")` on interfaces | YES |
| `@Authenticated` / `@Role` / `@Permission` / `@Idempotent` / `@RpcStream` / `@RpcTimeout` | Parsed & modeled |
| Stable CRC32 method IDs | YES (`service + method + params + return + version`) |
| Default HTTP mapping | `POST {path}/{methodName}` |
| `rpc-schema.json` | Written when `--emit-rpc` / `--emit-rpc-schema` |
| `--emit-rpc` human report | YES |

## 8–14. Wire / Codecs / Types

- Envelope: `{ "args": {...} }` request; `{ "ok": true, "result": ... }` / `{ "ok": false, "error": {code,message} }` response
- **Reuses `JsonCodec<T>`** — no second serializer
- Unsupported types (`NativePtr`, `Thread`, DOM, …) fail at analyze time
- Codec naming convention: `User` → `UserCodec` (hand-written or shared)

## 15–20. Generated Artifacts (no Class literals)

JNative has no `.class` literals, so DX is:

```java
UserApi_Rpc.mount(app, new UserApiImpl());
UserApi api = UserApi_Rpc.clientAt("http://127.0.0.1:18280");
UserApi local = UserApi_Rpc.client(); // same-origin / empty base
```

Generated:
- `UserApi_Rpc` helper
- `UserApi_RpcDispatch_*` WebHandlers — **direct** `impl.getUser(id)`
- `UserApi_RpcClient` implements `UserApi`

**No `Method.invoke` / reflection.**

## 21–30. Auth / Errors / Validation foundation

- Dispatcher checks `@Authenticated` / `@Role` via `RpcRuntime`
- Typed errors: `RpcException`, `RpcUnauthorizedException`, `RpcForbiddenException`, `RpcRemoteException`, …
- Unexpected exceptions → `INTERNAL_ERROR` (no stack leak to client)
- Validation / CSRF / streaming / OpenAPI: foundation / deferred detail in Known Limitations

## 31–40. Demo Acceptance

`examples/fullstack/rpc/App.java`

```text
/tmp/jnative-rpc-demo server   # :18280
/tmp/jnative-rpc-demo client
```

Observed:

```text
listUsers size=2
getUser id=1 name=Alice
createUser id=3
listUsers after create size=3
RPC_CLIENT_OK
```

- Shared `UserApi` interface  
- Zero handwritten server routes for RPC methods  
- Zero handwritten client HTTP for those methods  

## 41. Compiler Intrinsics

```text
RPC LLVM intrinsics: NONE
```

## 42. Architecture Acceptance

| Criterion | Status |
|-----------|--------|
| One interface for server/browser/desktop contract | **YES** (source); browser stub generation shared client class |
| Browser/native stubs generated | **YES** (`_RpcClient`; browser uses same HTTP helper foundation) |
| Server dispatch generated | **YES** |
| No runtime reflection | **YES** |
| JsonCodec reused | **YES** |
| Unsupported types fail compile-time | **YES** |
| Auth metadata on methods | **YES** |
| Typed remote errors | **YES** |
| Internal exceptions hidden | **YES** |
| WebUI without handwritten fetch | **PARTIAL** (RPC client ready; Admin WebUI migration next) |
| Desktop without handwritten HTTP | **YES** (native client) |
| Server without handwritten routes | **YES** |
| No JVM/JRE/JNI at runtime | **YES** |

## 43. Important Files

| Path | Role |
|------|------|
| `io/jnative/rpc/RpcAnalyzer.java` | Contract analysis |
| `io/jnative/rpc/RpcCodeGenerator.java` | Dispatcher + client source |
| `io/jnative/rpc/RpcSchemaGenerator.java` | Report + JSON schema |
| `stdlib/rpc/RpcLib.java` | Envelope / Http / errors |
| `examples/fullstack/rpc/App.java` | E2E demo |
| `src/test/java/io/jnative/rpc/RpcCompilerTest.java` | Compiler tests |

## 44. Known Limitations

- Sync unary RPC first (interface sync methods); `Future<T>` modeled but dispatcher path is sync WebHandler
- No method overloading → `client()` / `clientAt(url)` instead of overloaded `client`
- Streaming SSE/WebSocket RPC, OpenAPI export, schema-diff CLI, LocalRpcTransport: foundation / next
- Browser WASM still lacks full async Fetch→Future bridge; generated client is HTTP-native first
- Admin WebUI migration from 0.27 handwritten fetch: pending follow-up
- CSRF for cookie auth on mutating RPC: must still use 0.25 middleware when enabling cookie sessions

## 45. 0.29 Readiness

| Question | Answer |
|----------|--------|
| Incremental multi-module watch | NO |
| Precise invalidation graph | NO |
| IR / RPC codegen cache | NO |
| Parallel server+browser build | NO |
| Sub-second incremental rebuilds | NO |
| HMR / diagnostics streaming | NO |

→ **0.29** target: Incremental Compiler / Build Graph / Cache / Dev Server / Hot Reload.

---

**Verdict:** JNative **0.28 PASS** for compile-time typed unary RPC (shared interface → generated server dispatch + native client + schema), proven by `RPC_CLIENT_OK` E2E.
