# JNative One ERP — Release Blockers (1.5)

作者：张晓达

**Audit date:** 2026-10-08  
**Rule:** **P0 must be 0** before real customer pilot.  
**Platform baseline:** `docs/jnative-1.0-readiness.md` → `JNATIVE_1_0_NOT_READY`; `docs/1.0-release-blockers.md`.

---

## Summary

| Severity | Count (ERP productization view) | Impact |
|----------|----------------------------------|--------|
| **P0** | **≥ 6** | Blocks pilot |
| **P1** | several | Blocks “comfortable” production use |
| **P2** | UX / docs / polish | Acceptable for internal demos only |

---

## P0 — Must be zero for pilot

### ERP-P0-01 — Linux x86_64 native runtime not shippable

| Field | Value |
|-------|-------|
| Severity | P0 |
| Description | First-version deploy target is Linux x86_64. EventLoop epoll is a stub; Linux e2e **NOT VERIFIED**. |
| Evidence | `src/main/resources/native/posix/jnative_poll_epoll.c` header PARTIAL; `docs/platform-status.md` Linux x64 NOT VERIFIED; current `erp-server` is **macOS arm64 Mach-O only** |
| Impact | Cannot honestly offer server install packages for Linux customers |
| Status | **BLOCKED** |

### ERP-P0-02 — PostgreSQL wire / SCRAM / 08P01 instability

| Field | Value |
|-------|-------|
| Severity | P0 |
| Description | SCRAM unsupported on modern defaults; live sessions hit `invalid message format [SQLSTATE 08P01]`; migrate/query retries can storm. |
| Evidence | B-P0-05; enterprise re-run FAIL mid-suite; MIGRATE_SKIP hygiene does not fix wire |
| Impact | Data path unreliable under real PG |
| Status | **OPEN** |

### ERP-P0-03 — Transaction × await affinity unproven on PostgreSQL

| Field | Value |
|-------|-------|
| Severity | P0 |
| Description | Same physical connection across await not proven for PG (`pg_backend_pid`). |
| Evidence | B-P0-06 PARTIAL — SQLite TEMP verified; PG OPEN |
| Impact | Risk of split transactions / inconsistent posting |
| Status | **OPEN** |

### ERP-P0-04 — Password hashing not pilot-grade

| Field | Value |
|-------|-------|
| Severity | P0 |
| Description | PBKDF2-SHA256 default iterations = 10000 (improved from historical 500); Argon2id still missing; not OWASP-class for interactive logins at scale. |
| Evidence | `CryptoLib.DEFAULT_ITERATIONS = 10000`; B-P0-04 lineage |
| Impact | Credential stuffing / offline crack risk for deployed demos |
| Status | **OPEN** |

### ERP-P0-05 — Native TLS for product edge not verified

| Field | Value |
|-------|-------|
| Severity | P0 |
| Description | Product deploy assumes TLS (reverse proxy or native). Native TLS readiness not verified for ERP edge; secrets must not travel cleartext on untrusted networks. |
| Evidence | `deploy/env.example` defers TLS to proxy; Windows TLS incomplete; no ERP TLS e2e |
| Impact | Unsafe to expose admin/login on public IP without proven termination story |
| Status | **OPEN** (mitigation: require external TLS terminator — still unverified as packaged product) |

### ERP-P0-06 — No verified release install on clean target OS

| Field | Value |
|-------|-------|
| Severity | P0 |
| Description | Install scripts exist; clean Linux environment install **not executed**. No signed package, no checksum-gated release CI. |
| Evidence | B-P0-01 no CI; B-P0-07 no SDK distribution; this host is developer Mac |
| Impact | “Package” is not a product release |
| Status | **OPEN** |

### Related platform P0s (inherited, still count)

B-P0-01 CI · B-P0-02 Windows · B-P0-03 Linux epoll · B-P0-07 distribution · B-P0-08 (see 1.0 list) — ERP cannot declare independence from these for a JNative-native deploy.

---

## P1 — Main business usability

| ID | Issue | Evidence |
|----|-------|----------|
| ERP-P1-01 | WASM UI is menu shell, not full FormRenderer/DataTable product UI | `ErpApp.java` |
| ERP-P1-02 | Money is integer units, not Decimal | codebase-wide |
| ERP-P1-03 | Opening inventory / opening AR-AP-GL controlled flows incomplete | product status |
| ERP-P1-04 | Concurrency / idempotency matrix incomplete under load | NOT VERIFIED |
| ERP-P1-05 | Workflow not fully hooked to all approve HTTP paths | enterprise doc |
| ERP-P1-06 | Security OWASP suite not run | NOT VERIFIED |
| ERP-P1-07 | 30 min soak / GC stress on ERP server | NOT VERIFIED |
| ERP-P1-08 | Upgrade old-DB → new-app fixture not proven | scripts only |

---

## P2 — Experience

| ID | Issue |
|----|-------|
| ERP-P2-01 | Viewport matrix 1366/1440/1920 not automated |
| ERP-P2-02 | XLSX import not supported (CSV only) |
| ERP-P2-03 | Complex report designer intentionally out of scope |
| ERP-P2-04 | Demo credentials printed at boot (dev convenience) |

---

## Gate for pilot

```
P0 count == 0
AND Linux x86_64 native ERP server VERIFIED
AND backup restore drill VERIFIED on target OS
AND security + concurrency minimum suites PASS
AND business E2E (native + PG + WASM) PASS
```

**Current:** gate **FAIL** → no customer pilot.
