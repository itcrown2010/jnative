# JNative Core Completion Report — Round 4

**Theme:** I/O & Networking Integrity  
**Date:** 2026-10-08 (initial + closure pass)  
**Host:** macOS arm64 (Darwin), clang + OpenSSL present  

---

## 0. Preconditions

| Item | Result |
|---|---|
| Round 1 / 1.1 / 2 docs | Read |
| Round 3 | Stub — never formally closed; P0 gate used instead |
| resource-ownership / memory-model / platform-status / architecture-debt | Read |
| P0 gate (GC / Thread / Async / FFI / Resource) | **PASS** |
| Full suite UI/Graphics noise | Pre-existing `duplicate class Point/Size/Rect` — out of scope |

---

## Levels (Supported Profile)

| Area | Level | Evidence |
|---|---|---|
| IO Streams | **L2** | `StreamLib`; EOF=-1; `IoStreamEndToEndTest` (4) |
| ByteBuffer | **L2** | heap + bounds + pool; `ByteBufferEndToEndTest` (4) |
| DirectByteBuffer | **L0** | Not implemented |
| Filesystem | **L2+** | atomic write, walk/symlink cycle cap, streams; FS Round4 (4) + FileIo (13) |
| WatchService | **L2** | `pollEx` + OVERFLOW; watch e2e |
| DNS | **L2** | multi-address + NXDOMAIN; `DnsUdpEndToEndTest` |
| TCP | **L2+** | Network e2e (11); IPv6 `::1` PASS (`Ipv6TcpEndToEndTest`); 100 idle conns ≪100 threads |
| UDP | **L2** | bind/sendTo/receiveFrom echo |
| Async I/O | **L1+** | EventLoop kqueue real; AsyncTcp* still **pool façade**; JnFuture cancel-vs-complete PASS; **not** fd-polled 1k async DoD |
| TLS | **L2** | OpenSSL; handshake e2e; default verify rejects self-signed |
| HTTP Client | **L2+** | prior suite + CRLF reject + cross-origin Authorization strip |
| HTTP Server | **L2+** | prior suite + static traversal reject |
| HTTPS HTTP | **L2** | `HttpsNativeEndToEndTest` JNative↔JNative |
| HTTP/2 | **PARTIAL** | unchanged |
| Resource Integration | **L2** | tracker kinds; TWR streams; leak smokes |

---

## Closure pass deliverables (this turn)

### Security / HTTP

- `HttpHeaders` rejects CR/LF in names/values  
- Cross-origin redirect strips `Authorization` / `Proxy-Authorization` / `Cookie`  
- `HttpRequest.insecure(1)` for explicit TLS skip-verify (loud warning)  
- Pipelining explicitly **not supported** (`HttpClient.PIPELINING_SUPPORTED = 0`)  
- Tests: `HttpSecurityEndToEndTest` (3), `HttpsNativeEndToEndTest` (1)

### Async / TCP scale

- `connectAsync` / `readAsync` / `writeAsync` aliases  
- `AsyncIoRound4EndToEndTest`: cancel race, 100 idle TCP thread-count, GC-stress echo  
- IPv6 loopback e2e PASS on this host  

### Native / stdlib (from earlier Round 4)

- StreamLib, NetLib, stream FDs, DNS/UDP, writeAtomic, walk, ResourceTracker kinds  

---

## Test counts (macOS arm64)

| Bucket | Result |
|---|---|
| Round 4 initial e2e (ByteBuffer/Io/FS/DnsUdp/Tls) | 17 PASS |
| Closure e2e (HttpSecurity/Https/AsyncIo/Ipv6) | **7 PASS** (+ Http 12 / Tls 2 in same run) |
| Focused closure run | **22 PASS / 0 FAIL** (`HttpSecurity`+`Https`+`Tls`+`Ipv6`+`Http`+`AsyncIo`) |
| Core IO/net regression | **39 PASS** (ByteBuffer/Io/FS/Dns/Network/FileIo) |
| Explorer compile smoke | PASS (prior `/tmp/JnExplorerR4`; re-check this host) |
| Calendar / SchedulerServer compile | **FAIL** — pre-existing parse errors in merged stdlib+server (~line 15836+); **separate issue**, not Round 4 network regression |
| 30-minute IoNetworkSoak | **NOT RUN** |
| ASan/UBSan / Linux / Windows / WASM | **NOT VERIFIED** |

---

## Security results (explicit)

| Check | Result |
|---|---|
| Certificate verify (default) | PASS — self-signed rejected (`TlsNativeEndToEndTest`) |
| Hostname / chain path | PASS (fail closed) |
| insecureSkipVerify | Explicit + warning (Tls + HttpRequest) |
| CRLF header injection | PASS — rejected (`HttpSecurityEndToEndTest`) |
| Cross-host redirect Authorization | PASS — stripped |
| Static path traversal | PASS — rejected via `PathSafety` |
| HTTP request smuggling suite | **NOT expanded** beyond parser limits |

---

## Network / leak numbers

| Metric | Value |
|---|---|
| 100 idle TCP OS threads (client) | **&lt; 40** (asserted) — not thread-per-connection |
| TLS / HTTPS e2e | PASS |
| File tracker after 1k open/close | 0 (prior Round 4) |
| TCP echo / HTTP keepalive req/s | Not re-benchmarked |
| AsyncTcp 1k kqueue-driven | **NOT MET** |
| 30m soak | **NOT RUN** |

---

## Platform matrix

| Target | Status |
|---|---|
| macOS arm64 | **VERIFIED** |
| macOS x64 / Linux / Windows / wasm32-browser | NOT VERIFIED |

---

## P0 / P1

| Pri | Notes |
|---|---|
| P0 | None observed in Round 4 focused paths |
| P1 | AsyncTcp not on kqueue; dual cancel tokens; epoll/IOCP stub; DirectByteBuffer; SchedulerServer compile break (separate); no 30m soak |

---

## Definition of Done

| Gate | Status |
|---|---|
| Streams/Buffer L2+ | **MET** |
| Filesystem L2+ | **MET** |
| TCP IPv4/IPv6/partial/idle | **MET** (macOS) |
| Async 1k fd-poll / cancel I/O abort | **NOT MET** (Future cancel race MET; pool façade remains) |
| TLS + HTTPS HTTP e2e | **MET** |
| HTTP keep-alive/chunked/redirect + security basics | **MET** |
| 30m soak + cross-platform | **NOT MET** |

---

## Verdict

```
JNATIVE_CORE_ROUND4_PARTIAL
```

**Rationale:** IO / FS / TCP / UDP / DNS / TLS / HTTP(S) security baselines are at **L2+** on macOS arm64 with substantial new e2e. Async I/O DoD (true EventLoop fd multiplexing, per-op cancel aborting socket I/O, 1k async idle on kqueue) and 30-minute soak remain open — blocking `JNATIVE_CORE_ROUND4_PASS` / `PASS_PLATFORM_VERIFICATION_PENDING`.

**STOP** — do not enter Database / Redis / MQ / Game / new frameworks.
