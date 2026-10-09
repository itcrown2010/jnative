# JNative Security Review (1.0 Readiness)

作者：张晓达

**Audit date:** 2026-10-08  
**Scope:** TLS, HTTP parser, path traversal, JSON limits, SQL injection surface, package integrity, archive extraction, credentials logging, randomness, FFI boundaries, Auth/password handling.  
**Verdict:** **SECURITY GATE FAIL** for 1.0 Stable Auth / cross-platform HTTPS claims.

---

## 1. Executive summary

| Area | Status | Notes |
|---|---|---|
| TLS default certificate verify | **PASS (posix e2e)** | Self-signed rejected unless explicit insecure |
| TLS Windows | **FAIL** | No backend |
| HTTP CRLF injection | **PASS** | Native e2e rejects CR/LF in headers |
| HTTP redirect credential leak | **PASS** | Cross-origin strips Authorization/Cookie |
| HTTP static path traversal | **PASS** (suite claim) | Http security / server tests |
| Package archive traversal | **PASS** | `archiveRejectsPathTraversal` |
| Package hash tamper | **PASS** | Cache integrity test |
| SecureRandom (implementation) | **PASS (code)** | OS CSPRNG (SecRandom/getrandom/BCrypt) |
| SecureRandom (native e2e proof) | **FAIL** | No JNative-process e2e |
| Password hashing | **FAIL** | PBKDF2 default **500** iterations; Argon2id missing |
| SQL injection | **PARTIAL** | Prepared params exist; no adversarial suite |
| JSON depth/size limits | **PARTIAL** | Parser exists; limit abuse suite thin |
| Credentials in logs | **PARTIAL** | Not systematically audited |
| FFI boundary safety | **PARTIAL** | Effects/docs; not hardened |

---

## 2. TLS

| Control | Required | Evidence | Result |
|---|---|---|---|
| Certificate verify ON by default | YES | `TlsConfig.insecureSkipVerify = 0`; `TlsNativeEndToEndTest.defaultVerifyRejectsSelfSigned` | PASS (macOS/OpenSSL) |
| Hostname verify | YES | OpenSSL verify param / hostflags in `jnative_tls.c` | PARTIAL (depth limited) |
| SNI | YES | Present in TLS stack | PARTIAL (not full matrix) |
| TLS 1.2+ | YES | OpenSSL context | PARTIAL |
| Explicit insecure API is loud | YES | `HttpRequest.insecure` warning path (Round4) | PASS |
| Windows TLS | YES for Win matrix | Absent | **FAIL** |

**Rule:** Any default verification bypass is a 1.0 blocker. Current posix default is correct; Windows absence is a platform blocker.

---

## 3. HTTP security

| Test | Evidence | Result |
|---|---|---|
| CRLF in header name/value | `HttpSecurityEndToEndTest.crlfHeaderRejected` | PASS |
| Cross-origin redirect strips auth cookies | `crossOriginRedirectStripsAuthorization` + C fixture | PASS |
| Oversized headers | Not comprehensively evidenced | NOT VERIFIED |
| Malformed HTTP | Partial via parser paths | PARTIAL |
| Pipelining | Explicitly unsupported (`PIPELINING_SUPPORTED = 0`) | Documented OK |

---

## 4. Filesystem / path traversal

| Control | Evidence | Result |
|---|---|---|
| HTTP static traversal reject | Round4 / Http security notes | PASS (claimed + tests) |
| Package zip-slip | `Package030SuiteTest.archiveRejectsPathTraversal` | PASS |
| Symlink escape in packages | Partial | PARTIAL |
| App-level FS Unicode | FS Round4 e2e | PASS (macOS) |

---

## 5. JSON

| Risk | Evidence | Result |
|---|---|---|
| Malformed input | JsonLib + studio round-trip | PARTIAL |
| Unicode | Present in suites | PARTIAL |
| Large payload / depth bombs | No dedicated adversarial e2e found | **NOT VERIFIED** |
| Number precision (`getLong`) | Fix in `JsonParseLong`; **no native >INT_MAX regression** | **FAIL as regression gate** |

---

## 6. SQL / injection

| Control | Evidence | Result |
|---|---|---|
| Prepared statements (SQLite/PG) | Drivers + demos | PARTIAL |
| SCRAM auth | Explicitly unsupported | **FAIL for modern PG** |
| Injection adversarial corpus | Not found | NOT VERIFIED |
| Tx × await affinity | Host mirror only | **FAIL** (integrity) |

---

## 7. Package integrity

| Control | Evidence | Result |
|---|---|---|
| Content hash mismatch reject | PackageCache + tests | PASS |
| Offline after cache | CLI flag; thin unit proof | PARTIAL |
| Yank / audit | Package030 | PARTIAL |
| Supply-chain signing | Beyond hash | NOT VERIFIED |

---

## 8. Randomness

| API | Implementation | Proof |
|---|---|---|
| `SecureRandom` | `jnative_secure_random` → SecRandomCopyBytes / getrandom / BCryptGenRandom | **Code PASS** |
| Native e2e that bits are non-deterministic / from OS | **Missing** | Proof FAIL |
| Non-secure `Random` | Present in crypto/math paths | Must not be used for secrets |

**Rule satisfied in code:** SecureRandom is not a thin wrapper over `java.util.Random`.  
**1.0 gap:** prove it under JNative native execution.

---

## 9. Password / Auth

| Item | Status |
|---|---|
| Algorithm | PBKDF2-HMAC-SHA256 |
| Default iterations | **500** — **insecure for 1.0** |
| Argon2id / bcrypt | MISSING (planned comment only) |
| Event-loop blocking | Managed PBKDF2 loop — risk at higher iterations (doc admits crashes) |
| Plaintext password logging | Not fully audited — treat as open |

**Release requirement:** Auth cannot be Stable until KDF meets contemporary baselines **without** crashing the runtime.

---

## 10. FFI boundaries

| Item | Status |
|---|---|
| Owned vs borrowed docs | `resource-ownership.md` exists |
| GcHandle / callback foreign thread | E2E exists (`NativeCallbackEndToEndTest`) |
| Hardened misuse suite (UAF, double-free) | PARTIAL |
| Trusting user `void*` from managed heap | Dangerous if GC2 moves — docs conflict (see memory-model) |

---

## 11. Credentials & secrets hygiene

| Item | Status |
|---|---|
| Env-based secrets in demos | Present (DATABASE_URL etc.) |
| Logging redaction standard | NOT VERIFIED |
| Cert private keys in repo | `examples/https/certs/server.key` observed in workspace — **risk if published** |

---

## 12. Security gate decision

### `SECURITY_GATE_FAIL`

Must fix before 1.0 RC:

1. PBKDF2 default / Auth Stable policy (B-P0-04)  
2. JSON getLong native regression (B-P1-06)  
3. Live PG SCRAM or demote PG (B-P0-05)  
4. Native SecureRandom e2e (B-P1-08)  
5. Align GC moving semantics with FFI safety docs (B-P1-05)  
6. Remove or gitignore demo private keys from release artifacts  

---

## 13. What is already in good shape

- TLS fail-closed default on posix with e2e  
- HTTP header CRLF rejection  
- Redirect auth header stripping  
- Package zip-slip + hash tamper tests  
- OS CSPRNG wiring in C  

These are **L2 security islands**, not a full gate.
