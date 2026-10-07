# JNative Platform Status

> **Platform Source of Truth**  
> Audit date: 2026-10-07  
> Evidence basis: real source, real tests (`src/test`), real examples that compile/run.  
> **Not** based on Roadmap / README claims / historical design docs / TODOs / version plans.

## Legend

| Level | Meaning |
|---|---|
| **L0** | MISSING — absent, stub, or explicit reject |
| **L1** | WORKS — happy path executable / API exists |
| **L2** | TESTED — unit + negative + integration (or solid e2e suite) |
| **L3** | PRODUCTION-CAPABLE — error paths, cleanup, concurrency where needed, real app use |
| **L4** | HARDENED — stress/soak/race/cross-platform/perf regression |

Special (not L0–L4): `PLANNED` · `EXPERIMENTAL` · `DEPRECATED` · `BLOCKED`

Rules:

- Planned ≠ Implemented ≠ Working ≠ Tested ≠ Production Ready ≠ Hardened
- Evidence must cite code path and/or test name
- “Code exists” alone is never enough for ≥ L2
- Cross-platform: only platforms with **real compile+run** evidence count

---

## Inventory Snapshot (auto-scan)

| Bucket | Count / note |
|---|---|
| Compiler Java (`src/main/java/io/jnative`) | ~291 `.java` files |
| Stdlib units | 23 `.java` files under `src/main/resources/stdlib/{async,browser,collections,crypto,db,editor,graphics,http,io,json,rpc,studio,time,tls,ui,web,webui}` |
| Native sources | posix + windows; ~25 C/ObjC/H units (posix fuller) |
| Tests | **63** `src/test/**/*.java`; **24** e2e classes under `e2e/` |
| Empty test dirs | `string/`, `rtti/`, `processor/`, `constructor/`, `array/` |
| Examples with `main` | ~75 |
| Host `@Test` methods | counted via scan (tens–low hundreds; many host-mirror) |

### Native platform matrix (source presence ≠ tested)

| Target | Native sources | Real e2e evidence in CI/local suite |
|---|---|---|
| macOS arm64/x86_64 | posix + Cocoa UI | **Yes** (primary clang host for e2e) |
| Linux x86_64 | posix + epoll **PARTIAL stub** | Socket/HTTP/GC code paths; poll/epoll not real |
| Linux arm64 | same sources | **No** dedicated matrix evidence |
| Windows x86_64 | fs/socket/ui_win32/poll_iocp **PARTIAL** | **No** TLS; IOCP stub; no Windows e2e gate |

---

## JNative Platform Health (summary)

| Area | Level | One-line evidence |
|---|---|---|
| Lexer / Parser | L3 | Parser ~1.7k LOC; used by all e2e |
| Semantic / Type check | L2–L3 | Large SemanticAnalyzer; many language gaps hard-rejected |
| IR Builder | L3 | IrGenerator drives all backends |
| Optimizer | L2 | `OptimizerEndToEndTest` thin; opt unit tests exist |
| LLVM Backend | L3 | Primary native path for e2e |
| WASM Backend | L2–L3 | `WasmBrowserTargetTest` + scheduler/browser demos |
| Linker / build | L3 | Incremental029 + Package030 suites |
| GC (gc2) | L3 | `GcEndToEndTest` + `Gc2EndToEndTest` (stress/stats); **no Windows GC** |
| Exceptions | L3 | `ExceptionEndToEndTest` (13 cases) |
| String | L2–L3 | Builtin APIs + IR; no dedicated string suite; **no StringBuilder** |
| Math | **L0** | No `Math` class in stdlib/builtins |
| Time | **L1** | `TimeLib` Instant/Zone/Local*; DST hardcoded; **no Time e2e** |
| Collections | L2–L3 | ArrayList/HashMap/HashSet/ArrayDeque + `CollectionsEndToEndTest` |
| Filesystem | L3 | `FileIoEndToEndTest` + Explorer app |
| Concurrency (Thread/Mutex/Atomic) | L2–L3 | Builtins + `ThreadEndToEndTest` |
| Future / Async | L2 | Dual `Future` + `JnFuture`; thin async e2e |
| EventLoop poll | **L1 / BLOCKED** on Linux/Win | **kqueue real**; epoll/IOCP PARTIAL stubs |
| Networking TCP | L3 | `NetworkEndToEndTest` |
| UDP | **L0** | No API |
| TLS | L2 | OpenSSL posix; no handshake e2e; no Windows TLS |
| HTTP/1.1 | L3 | `HttpEndToEndTest` |
| HTTP/2 | L2 | HPACK/frame host tests; no live h2 peer e2e |
| WebSocket | L2–L3 | RealtimeLib + chat/ws_echo; mostly host-mirror tests |
| JSON | L2–L3 | JsonLib + Meta codec gen; getLong fix **untested** for large ints |
| RPC | L2–L3 | Compiler/Meta tests; scheduler uses REST mirror |
| SQLite | L2–L3 | FFI + scheduler/chat/db demos |
| PostgreSQL | L2 | Protocol + MD5; **SCRAM unsupported**; no live-PG e2e gate |
| MySQL | **L0** | Explicitly rejected by registry |
| Redis / Kafka / Jobs / Cache | **L0** | Absent from stdlib |
| Crypto | L2 | SecureRandom/SHA256/HMAC/PBKDF2/JWT; **no AES/Argon2**; host mirrors |
| Native UI / Graphics / Editor | L2–L3 | Ui/Gfx/Editor e2e + notepad/explorer |
| Studio | L2–L3 | `StudioEndToEndTest` + example |
| WebUI / Browser | L2–L3 | WebUiReactiveTest + WASM demos |
| Package Manager | L3 | `Package030SuiteTest` (lockfile/cache/yank/stress) |
| Incremental Build | L3 | `Incremental029SuiteTest` |
| Observability (metrics/trace) | L0–L1 | Process counters only; no OTel |
| Cross-platform hardening | **L0–L1** | No multi-OS CI maturity |

**No area audited as L4.** Stress exists for GC only in limited form; no soak/race/cross-platform gate suite.

---

## 1. Language Core

| Capability | Level | Evidence | Missing | Blocker |
|---|---|---|---|---|
| Lexer | L3 | `lexer/*` + all compile paths | Fuzz corpus | — |
| Parser | L3 | `Parser.java` ~1764 LOC | Grammar fuzz | — |
| AST | L3 | `ast/*` used end-to-end | — | — |
| Variables / Constants | L3 | Language e2e (`EndToEndTest`, examples) | — | — |
| Primitive Types | L3 | Used everywhere | Exhaustive conversion suite | — |
| Arithmetic / Comparison / Logical | L3 | Examples + e2e | Bit-exact suite | — |
| Bitwise Operators | L2 | Present in language | Dedicated bitwise tests | — |
| if / while / for / return | L3 | Widespread e2e | — | — |
| switch | L2 | Parser/semantic support (verify per use) | Exhaustive switch e2e | — |
| **break / continue** | **L0** | No BREAK/CONTINUE in `TokenType` | Keywords + CFG | Language gap |
| **ternary `?:`** | **L0** | `?` used for wildcards only | Parser + lowering | Language gap |
| methods | L3 | Methods/e2e | — | — |
| **method overload** | **L0** | `SemanticAnalyzer`: "method overloading is not supported yet" | Signature index | **Blocks Math API shape** |
| constructor overload | L2 | Allowed (duplicate ctor rejected by signature) | Broader suite | — |
| classes / fields / constructors | L3 | Widespread | — | — |
| static / this / super | L3 | Inheritance e2e paths | — | — |
| inheritance / override | L3 | `DynamicDispatch`, vtable docs+e2e | — | — |
| interfaces / abstract | L3 | `InterfaceEndToEndTest` | default methods | default/static iface methods rejected |
| enum | L2 | Used in apps (scheduler Enums) | Full enum semantics suite | — |
| arrays | L3 | Array runtime + e2e usage | Dedicated array suite (dir empty) | — |
| generics | L2–L3 | `GenericsEndToEndTest` + lowering | Wildcards/inference depth | — |
| lambda / SAM | L2–L3 | `CallableEndToEndTest`, `LambdaClosure` | Capture edge cases | — |
| method reference | L1–L2 | Partial if present via callable | Full Java-parity suite | — |
| exceptions try/catch/finally/throw | L3 | `ExceptionEndToEndTest` | Multi-catch, checked throws | — |
| **try-with-resources** | **L0** | `TryStatement` has no resources | AST + AutoCloseable | Language gap |
| annotations | L2–L3 | Meta processors (@Rpc/@Config/@Entity…) | Retention completeness | — |
| **records** | **L0** | No `record` token/AST | Full feature | — |
| **pattern matching** | **L0–L1** | Classic `instanceof` only | Bindings / switch patterns | — |
| async/await | L2 | IR async + `AsyncEndToEndTest` (thin) | Exception/finally/cancel matrix | Dual Future model |
| null semantics | L2 | NPE paths in exception e2e | Exhaustive nullability | — |

**Language Core targets (from audit goals):** Primitive/Control/Methods/Classes/Inheritance/Interfaces/Arrays/Exceptions/Generics/Lambda → **none are L4**; several foundational control/API features are still **L0**.

---

## 2. Type System

| Capability | Level | Evidence | Missing | Blocker |
|---|---|---|---|---|
| primitive conversion / promotion | L2 | SemanticAnalyzer + compile success | `tests/typesystem/` suite | Dir does not exist |
| reference / subtyping | L2–L3 | Inheritance/interface e2e | Formal suite | — |
| overload resolution | **L0** | Overload rejected | Entire feature | Blocks APIs |
| generic substitution / bounds | L2 | Generics e2e | Bounds edge cases | — |
| wildcards | L1–L2 | `?` parsing exists | Full capture conversion | — |
| type inference / SAM / lambda inference | L2 | Callable e2e | Hard cases | — |
| null | L2 | Runtime NPE | Static null analysis | — |
| native pointer types | L2–L3 | FFI e2e / NativeDataModel | Escape edge cases | — |
| array covariance | L1–L2 | Runtime arrays | Explicit covariance tests | — |

---

## 3. Compiler Pipeline

| Layer | LOC (approx) | Tests | Known issues | Maturity |
|---|---|---|---|---|
| Lexer | small package | lexer tests exist | — | L3 |
| Parser | ~1764 (`Parser.java`) | parser tests | Rejects unsupported syntax | L3 |
| Semantic Analyzer | large | semantic/* | Hard rejects overload/break/… | L2–L3 |
| Type Checker | embedded in semantic | partial | No `tests/typesystem/` | L2 |
| IR Builder | IrGenerator large | ir/* + all e2e | Async/exception complexity | L3 |
| Optimizer | `opt/*` | `opt/*` + `OptimizerEndToEndTest` (2 tests) | Opt vs -O0 consistency thin | L2 |
| LLVM Backend | LlvmIrGenerator + emitters | all native e2e | App semantics mostly via jnative_* not LLVM hacks | L3 |
| WASM Backend | wasm package | WasmBrowserTargetTest | GC/exception/browser gaps | L2–L3 |
| Linker / session | CompilerSession, pkg | Incremental029, Package030 | Multi-module was demo-blocker (dependsOn) | L3 |

### IR leakage note

- `NativeIntrinsic` enum is narrow: `ADDRESS_OF`, `LOAD_INT`, `STORE_INT`, `ALLOCATE_STRUCT`.
- LLVM `@llvm.*` usage observed: **`llvm.memset.p0.i64` only**.
- Most “intrinsics” are **`jnative_*` runtime calls** emitted by backend — acceptable layering if IR stays language-level. No broad LLVM-semantic leakage found in this audit pass; keep watching optimizer/GC emitters.

---

## 4. Optimizer (per-pass)

| Pass | Level | Evidence | Missing |
|---|---|---|---|
| constant folding / propagation | L2 | opt package + thin e2e | -O0/-O2/-O3 identity suite |
| DCE / CFG simplify | L2 | opt unit tests | Same |
| SSA | L2 | IR form | Documented passes |
| inlining / devirtualization | L1–L2 | docs + opt code | Correctness under virtual dispatch |
| escape / stack / scalar replace | L1–L2 | docs (`escape-analysis`, `stack-allocation`) | Hardening tests |
| bounds / null-check / safepoint elim | L1–L2 | docs + emitters | Miscompile regression suite |
| whole-program DCE | L1 | Incremental/fingerprint related | Explicit WPDCE tests |

**Verdict:** Optimizer **not L3+** — insufficient opt-level identity + stress evidence.

---

## 5. Runtime Core

| Capability | Level | Evidence | Missing | Blocker |
|---|---|---|---|---|
| allocation / layout / metadata | L3 | GC + object model + e2e | Windows GC | — |
| vtable / itable / RTTI | L3 | Interface/inheritance e2e; rtti test dir empty | Dedicated rtti suite | — |
| instanceof / cast | L3 | Exception + interface paths | Pattern bind | — |
| array / string runtime | L3 / L2–L3 | Emitters + e2e | StringBuilder | — |
| exception runtime | L3 | ExceptionRuntimeEmitter + e2e | Async boundary matrix | — |

---

## 6. GC

| Capability | Level | Evidence | Missing | Blocker |
|---|---|---|---|---|
| allocation / roots / mark / sweep | L3 | `jnative_gc2.c` + Gc* e2e | Windows port | **No Win GC** |
| young / old / TLAB / barrier | L3 | gc2 + Gc2EndToEndTest | Card-table soak metrics | — |
| promotion | L2–L3 | gc2 paths | Metrics dashboard | — |
| static/thread/exception/async/native roots | L2–L3 | docs + code paths; e2e partial | FFI/async stress matrix | — |
| stress | L2–L3 | GcEndToEndTest stress/stats | Multi-thread soak hours | — |
| GC Dashboard metrics | L1 | stats APIs in tests | p50/p95/p99 pause productization | — |

| Metric | Recorded value |
|---|---|
| max tested heap | Not published as platform metric |
| max object count | Not published |
| longest soak | Not published (no soak harness) |
| Windows GC | **Absent** |

---

## 7. Exception Runtime

| Capability | Level | Evidence | Missing |
|---|---|---|---|
| throw / catch / finally / subtype | L3 | ExceptionEndToEndTest | Multi-catch |
| uncaught / stack trace | L2–L3 | uncaught covered; full traces partial | Suppressed / rich traces |
| constructor exception | L3 | e2e | — |
| async exception / native boundary | L1–L2 | Limited | Dedicated matrix → needed for L4 |

**Target L4:** not met.

---

## 8. Native Interop / FFI

| Capability | Level | Evidence | Missing | Blocker |
|---|---|---|---|---|
| @Native / C ABI | L3 | FfiEndToEndTest, sqlite demos | Broader ABI types | — |
| NativePtr / NativeStruct / CString / NativeMemory | L2–L3 | NativeDataModel e2e + demos | Union/bitfield | — |
| callbacks / function pointers | L2–L3 | NativeCallbackEndToEndTest | Lifetime stress | — |
| stack allocation / address-of | L2–L3 | NativeIntrinsic + e2e | Escape fuzz | — |
| FFI safety (ownership/double-free/GC) | L2 | Effects/contracts docs + some tests | Hardened suite | — |

---

## 9. Standard Library — lang / Math / Time / Collections / IO

### lang

| Capability | Level | Evidence | Missing |
|---|---|---|---|
| Object / String | L3 / L2–L3 | Builtins + IR | StringBuilder; string suite |
| wrappers (Integer…) | L1–L2 | Partial via valueOf etc. | Full wrapper API |
| System / Runtime / Process | L2–L3 | ProcessBuilder in Studio e2e; Env in IoLib | Windows process matrix |
| **Math** | **L0** | No class | Entire module + overload policy |
| **StringBuilder** | **L0** | Absent | Implementation |

### Math Advanced

| Capability | Level | Evidence |
|---|---|---|
| BigInteger / BigDecimal | **L0** | Absent |
| Random / SecureRandom | L2 / L2 | CryptoLib SecureRandom native fill; Random not full Math suite |

### Time (itemized)

| API | Level | Evidence |
|---|---|---|
| Instant | L1–L2 | wallNow→wallMillis; now=mono |
| Duration | L1 | TimeLib |
| Period | L0–L1 | Not a first-class hardened type |
| LocalDate/Time/DateTime | L1 | CivilDate helpers; **no e2e** |
| ZoneId / ZonedDateTime | L1 | Fixed offsets; **DST PARTIAL** (comment in TimeLib) |
| Clock / DateTimeFormatter / IANA tzdata | L0–L1 | No IANA; formatting helpers limited |
| Leap/DST/epoch boundary tests | **L0** | No suite |

### Collections (itemized)

| API | Level | Evidence |
|---|---|---|
| Iterable/Iterator/List/ArrayList | L2–L3 | CollectionsEndToEndTest |
| LinkedList | **L0** | Absent |
| HashSet / HashMap | L2–L3 | Same |
| TreeSet / TreeMap | **L0** | Absent |
| ArrayDeque | L2–L3 | Present |
| PriorityQueue | **L0** | Absent |
| Optional / Comparator / Comparable | L1–L2 | Comparator/Comparable present; Optional thin/absent |
| Concurrent collections | **L0–L1** | No ConcurrentMap etc. |

### IO / Filesystem

| Capability | Level | Evidence | Missing |
|---|---|---|---|
| Streams / Reader/Writer hierarchy | L1 | Partial via Files/Bytes helpers | Full java.io parity |
| ByteBuffer / DirectByteBuffer / BufferPool | **L0–L1** | No ByteBuffer class found in stdlib | Core networking buffer model |
| Path / Files CRUD | L3 | FileIoEndToEndTest | — |
| directory walk / metadata | L2–L3 | IoLib + Explorer | Permissions matrix |
| symlink / junction / UNC / long path | L1 | Platform C code partial | Explicit tests per OS |
| WatchService | L1–L2 | IoLib + kqueue/inotify/Win; **no jnative Watch e2e** | Dedicated e2e |

---

## 10. Concurrency / Executor / Future / Cancellation / Async

| Capability | Level | Evidence | Missing | Blocker |
|---|---|---|---|---|
| Thread / Mutex / Condition / Atomic* | L2–L3 | Builtins + ThreadEndToEndTest | Race/TSan suite | — |
| ThreadLocal | L1 | Exception TLS etc. | User ThreadLocal API suite | — |
| ReentrantLock / RWLock / Semaphore / Latch / Barrier | L0–L1 | AsyncSemaphore exists; Java-parity locks thin | Full set | — |
| BlockingQueue / ConcurrentMap | **L0** | Absent | — | — |
| ThreadPoolExecutor | L2 | CollectionsLib + workers | Rejection/timeout policy tests | — |
| **Future (language) vs JnFuture** | L2 (split) | Builtin Future + `AsyncLib.JnFuture` | **One Async Model** | API debt |
| Promise / AsyncResult / Task types | — | Promise/AsyncResult **absent**; PoolTask/LoopTask exist | Naming cleanup | — |
| CancellationToken ×2 | L1–L2 | `AsyncLib.JnCancellationToken` + `IoLib.CancellationToken` | Unify | Duplicate |
| Deadline type | **L0** | Only `deadlineMs` fields | Unified Deadline | — |
| async/await + EventLoop | L2 | Async e2e thin; EventLoop real on kqueue | Linux/Win poll | **epoll/IOCP stubs** |
| async exception/finally/cancel | L1–L2 | Partial | Matrix | — |

### EventLoop platform

| Backend | Status |
|---|---|
| kqueue (macOS) | **Implemented** (real wait/register paths) |
| epoll (Linux) | **PARTIAL stub** — create/destroy/wakeup; wait returns 0 |
| IOCP (Windows) | **PARTIAL stub** — does not fake with select |

---

## 11. Networking / TLS / HTTP / WebSocket

| Capability | Level | Evidence | Missing | Blocker |
|---|---|---|---|---|
| DNS / TCP client/server | L3 | NetworkEndToEndTest | — | — |
| IPv4/IPv6 | L2–L3 | getaddrinfo paths | Explicit v6 suite | — |
| timeout / partial / EOF | L2–L3 | Network e2e | Backpressure soak | — |
| **UDP** | **L0** | Absent | Entire feature | — |
| TLS 1.2/1.3 | L2 | `jnative_tls.c` OpenSSL | Live handshake e2e; Win TLS | No Win TLS source |
| cert verify / SNI / ALPN | L1–L2 | TlsLib + TlsAbstraction031Test | Real peer tests | — |
| session resumption / cert reload | L0–L1 | Not evidenced | — | — |
| HTTP/1.1 | L3 | HttpEndToEndTest | — | — |
| HTTP/2 multiplex/HPACK/flow | L2 | Http2*031 host tests | Live h2 | — |
| WebSocket | L2–L3 | RealtimeLib + examples | Native e2e vs host mirror | — |

---

## 12. JSON / RPC / Web / WebUI / Browser

| Capability | Level | Evidence | Missing |
|---|---|---|---|
| JSON parser/writer | L2–L3 | JsonLib + apps | Large-number getLong tests |
| Generated codec | L3 | Meta032 + JsonProcessor | — |
| @Rpc processor/schema/client | L2–L3 | RpcCompilerTest + Meta | Full runtime e2e (scheduler REST) |
| Router/middleware/static/CORS | L2–L3 | WebLib + examples | CSRF/rate-limit productization |
| WebUI Component/State/Effect | L2–L3 | WebUiReactiveTest | ErrorBoundary/router depth |
| Browser DOM/Fetch/Timer | L2–L3 | BrowserLib + wasm tests | Web Crypto completeness |

---

## 13. SQL / Cache / Redis / Jobs / Messaging

| Capability | Level | Evidence | Missing | Blocker |
|---|---|---|---|---|
| Database/DataSource/Pool/Tx | L2 | DbLib + DatabaseStackTest (host mirror) | Live multi-DB e2e | — |
| SQLite | L2–L3 | FFI + demos (scheduler/chat) | Stress | — |
| PostgreSQL connect/query/tx | L2 | PgDriverLib + protocol tests | Live PG gate | — |
| SCRAM | **L0–L1** | Explicit "not yet supported" | SCRAM impl | Auth blocker for many hosts |
| PG types matrix | L1–L2 | Partial decode | Full type table tests | — |
| MySQL | **L0** | Registry throws | — | — |
| Cache/Redis/Jobs/Kafka/Rabbit/EventBus | **L0** | Not in stdlib | — | Do not prioritize vs Core |

---

## 14. Config / Logging / Crypto / Compression / Resilience / Observability

| Capability | Level | Evidence | Missing |
|---|---|---|---|
| Typed config (@Config) | L2 | ConfigProcessor + Meta032 | Reload/secrets hardening |
| Logger | L1 | IoLib.Logger stdout | Structured/JSON/rolling/async |
| Crypto core | L2 | CryptoLib + Security* host tests | Native crypto e2e; AES; Argon2 |
| Argon2id / AES-GCM / ChaCha / RSA / Ed25519 | **L0** | Absent (Argon2 “planned” comment only → **PLANNED**, not L0 feature credit) | — |
| Compression (gzip/zstd/…) | L0–L1 | Not evidenced as stdlib module | — |
| Retry/CircuitBreaker/Bulkhead | **L0–L1** | Auth RateLimiter only | Resilience library |
| Metrics Prometheus | **L0** | In-process counters only | — |
| Tracing / traceparent | **L0** | Absent | — |

---

## 15. Process / CLI / Validation / Native UI / Graphics / Editor / Studio

| Capability | Level | Evidence | Missing |
|---|---|---|---|
| ProcessBuilder | L2–L3 | Studio processEcho (`/bin/echo`) | Windows |
| CLI framework | L1–L2 | CliArguments (multi-file limits noted) | Completion/validation product |
| Validation annotations | L2–L3 | Meta validation generation | Full constraint set |
| Native UI | L2–L3 | Ui* e2e + Explorer/Notepad | Win matrix CI |
| Graphics | L3 | GraphicsEndToEndTest | — |
| Code Editor | L3 | EditorEndToEndTest + Notepad | Huge-file soak |
| Studio | L2–L3 | StudioEndToEndTest | Full IDE feature parity |

---

## 16. Build / Package / Meta / DI / Test / Benchmark / Tooling

| Capability | Level | Evidence | Missing |
|---|---|---|---|
| Project/modules/incremental/cache | L3 | Incremental029SuiteTest | Artifact hash clean=incremental identity published metric |
| Package manager lockfile/cache/publish | L3 | Package030SuiteTest | Registry prod + signatures |
| Package security | L2 | cache integrity/yank tests | Signing foundation; confusion suite |
| Metaprogramming processors | L3 | Meta032 | Permission model hardening |
| Compile-time DI | L2–L3 | UserServiceFactory generation in meta example | Cycle suite completeness; **no runtime reflection** by design |
| Test framework (JNative-native) | L1 | Host JUnit drives compiler e2e | First-class `@Test` runtime |
| Benchmark harness | L0–L1 | No `@Benchmark` product | — |
| Tooling CLI (build/run/test/fmt/lint/doc/doctor) | L1–L2 | Compiler/CLI entrypoints exist unevenly | Full DX surface |

---

## 17. Diagnostics

| Class | Level | Note |
|---|---|---|
| Syntax errors | L2–L3 | Parser diagnostics |
| Type errors | L2 | Usable; not L4 polish |
| Generic errors | L2 | — |
| Native / link / capability errors | L1–L2 | Improving via effects/capabilities | Uneven messages |
| Runtime errors | L2–L3 | Exception paths | Stack traces incomplete vs JVM |

---

## 18. Real Applications

| App | Level as product | What it proves | Gaps |
|---|---|---|---|
| **Explorer** | L2–L3 demo | Native UI + Files + Watch refresh + TableView | No dedicated test suite; platform matrix informal |
| **Notepad** | L2–L3 demo | Editor + UI + file I/O + undo/find | Relies on Editor/UI e2e |
| **Scheduler (Calendar)** | L2 demo | Browser shell + HTTP + SQLite + DTO/validation; optional PG URL | Not full WASM↔Typed RPC↔PG chain; REST mirror of @Rpc; Chinese search PARTIAL historically |
| **Chat** | L2 demo | Auth + WS hub + SQLite | Security tests often host-mirror |
| **Studio** | L2 demo | Tooling shell + process + editor | Not full IDE |
| **MetaApp** | L2 | DI/JSON/RPC/Config generation | Example-scale |

**Calendar critical chain status:**

```text
Browser WASM → Typed RPC → Native Server → PostgreSQL → DTO → WASM → WebUI
```

| Hop | Reality |
|---|---|
| Browser WASM shell | Present (scheduler/browser + public JS) |
| Typed RPC end-to-end | **Partial** — contracts exist; server path is REST-shaped |
| Native Server | Present (HTTP) |
| PostgreSQL | Optional URL; SQLite default; SCRAM missing |
| DTO / WebUI | Partial (shared Java + browser UI) |

---

## 19. Duplicate Abstractions (search)

| Concept | Instances | Verdict |
|---|---|---|
| Future | Language builtin `Future` + `JnFuture` | **Unify** |
| Cancellation | `JnCancellationToken`/`CancellationSource` + `IoLib.CancellationToken` | **Unify** |
| Deadline | No type; `deadlineMs` fields | Introduce one |
| Promise / AsyncResult | Absent | Do not invent third Future |
| Buffer / ByteBuffer | Bytes helpers; no ByteBuffer | Gap, not duplicate |
| Pool | ThreadPoolExecutor; DB pool | OK distinct domains |
| Logger / Config / Result | Single primary each | OK |
| Date/Time | TimeLib only (docs may overclaim) | OK |

---

## 20. Compiler Intrinsics

### `NativeIntrinsic` (complete)

| Name | Reason | Owner |
|---|---|---|
| ADDRESS_OF | FFI address-of | semantic + IR |
| LOAD_INT | typed native load | semantic + IR |
| STORE_INT | typed native store | semantic + IR |
| ALLOCATE_STRUCT | native struct alloc | semantic + IR |

### FFI-intrinsic methods (String/runtime)

String methods marked `setFfiIntrinsic(true)` in SemanticAnalyzer (length, charAt, fromBytes, valueOf, …) — lowered in IR/LLVM, not via NativeIntrinsic enum.

### LLVM intrinsics

| Name | Use |
|---|---|
| `llvm.memset.p0.i64` | zero-init / copy helpers in LlvmIrGenerator |

---

## 21. Runtime Native Dependencies

| Domain | Dependency | Ownership/error notes |
|---|---|---|
| libc / POSIX | sockets, fs, threads, process | Primary posix runtime |
| kqueue | EventLoop poll (macOS) | Real |
| epoll / IOCP | stubs | **BLOCKED** for portable async net |
| OpenSSL | `jnative_tls.c` | Posix only |
| SQLite | system/lib link via FFI demos | Blocking worker model |
| Cocoa / Win32 | UI | Dual backends; test bias macOS |
| Graphics | native graphics C | e2e present |

---

## 22. TODO / Stub / Fake-implementation audit

### Real stubs (BLOCKED)

- `jnative_poll_epoll.c` — PARTIAL
- `jnative_poll_iocp.c` — PARTIAL (explicitly does not fake IOCP)

### Explicit language rejects (not TODOs)

- method overloading
- break/continue (absent tokens)
- try-with-resources
- records / pattern matching
- default interface methods

### Partial features

- TimeLib DST (hardcoded offsets)
- PostgreSQL SCRAM
- ORM (`ormNotImplemented` test marker)
- language-support.md is **stale** (claims no TLS/WS/collections) — **do not trust for maturity**

### Fake / host-mirror risk

Many `Security*`, `Http2*`, `PgProtocol*`, `DatabaseStack*` tests are **JVM host mirrors**, not native binary contracts. They support ≤ L2 unless paired with native e2e.

---

## 23. Weekly quality metrics (baseline — fill going forward)

| Metric | Baseline 2026-10-07 |
|---|---|
| P0 count | See architecture-debt.md (known candidates) |
| P1 count | See architecture-debt.md |
| L0 / L1 / L2 / L3 / L4 capability counts | Dominated by L0–L2; **L4 = 0** |
| tests total | 63 test files |
| native E2E classes | 24 |
| stress tests | GC stress present; general stress harness absent |
| cross-platform tests | **No matrix gate** |
| compiler crashes / 10k builds | **Unmeasured** |
| runtime crashes / 1k test hours | **Unmeasured** |
| leak metrics | **Unmeasured** |
| build metrics (clean/incremental) | Suite exists; dashboard numbers unpublished |
| perf benchmarks | **No fixed public dashboard** |

---

## 24. Test coverage gaps (high level)

1. `tests/typesystem/` — **missing**
2. Math / Time / StringBuilder — **missing**
3. getLong large-integer — **missing**
4. Live TLS handshake / live PostgreSQL / live HTTP2 — **missing**
5. WatchService jnative e2e — **missing**
6. Windows / Linux CI matrix — **missing**
7. Async cancel/exception/finally matrix — **thin**
8. Optimizer -O0/-O2/-O3 identity — **thin**
9. 10,000+ semantic corpus — **aspirational; far from current ~tens of e2e classes**

---

## Audit seal

```text
JNATIVE_PLATFORM_AUDIT_COMPLETE
```

Companion document: [architecture-debt.md](architecture-debt.md)
