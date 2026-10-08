# JNative One — Integration Status (1.6 audit)

**Audit date:** 2026-10-08  
**Code root:** `examples/erp` (only ERP product tree under `examples/`)  
**Host:** Darwin arm64 · binary `examples/erp/.jnative/build/erp-server` (Mach-O arm64)  
**Rule:** Prior stamps are historical claims — re-verified only where evidence below says so.

Levels: **MISSING** · **IMPLEMENTED** · **RUNNABLE** · **TESTED** · **INTEGRATED** · **PILOT_READY**

---

## Prior stage claims (not re-certified as PILOT_READY)

| Stage | Document stamp |
|-------|----------------|
| Phase-1 | `JNATIVE_ERP_PHASE1_PARTIAL` |
| Finance 1.2 | `PARTIAL` |
| Manufacturing 1.3 | `PASS` (doc) |
| Enterprise 1.4 | `PARTIAL` |
| Productization 1.5 | `BLOCKED` |
| Platform 1.0 | `JNATIVE_1_0_NOT_READY` |

---

## Module matrix (evidence-based, 1.6 re-run)

| Module | Level | Evidence |
|--------|-------|----------|
| Native server (macOS arm64) | **TESTED** | `file …/erp-server` → Mach-O arm64; headless suites run |
| Native server (Linux x86_64) | **MISSING** | epoll stub; no ELF binary e2e |
| PostgreSQL wire | **RUNNABLE** (flaky) | Connect + suites; intermittent `08P01` still observed (esp. mfg) |
| SCRAM auth | **MISSING** | PgDriverLib unsupported |
| Purchase→GR→SO→DL qty loop | **INTEGRATED** | `--integration-test` **PASS** on PG + SQLite (2026-10-08) |
| Full AP/AR invoice→payment→GL | **TESTED** | `--finance-test` **PASS** on PG + SQLite |
| Inventory balance vs txn | **TESTED** | `ErpIntegrity.checkInvBalance` in integration |
| Manufacturing BOM/WIP/MRP | **TESTED** (flaky PG) | SQLite **PASS**; PG intermittent `08P01` after BOM explode |
| RBAC / company isolation | **TESTED** | `--enterprise-test` **PASS** on PG; SQLite flaky prepare |
| Workflow / custom fields | **TESTED** | enterprise suite on PG |
| Browser HTML console | **RUNNABLE** | `public/index.html` ASCII UI |
| Browser WASM ErpApp | **IMPLEMENTED** / not INTEGRATED | build exists; loader/UI-thread hang historically |
| Tx × await same PG connection | **MISSING** / **BLOCKED** | B-P0-06 OPEN; posting uses sync `blockingWait` |
| Concurrency 100× | **MISSING** | NOT_VERIFIED |
| Fault injection | **MISSING** | NOT_VERIFIED |
| 30 min soak | **MISSING** | NOT_VERIFIED |
| Decimal money | **MISSING** | integer cents/units |
| Integrity checker | **TESTED** | `--integrity-check` / post-logic in `--integration-test` |
| Posting 08P01 retry | **IMPLEMENTED** | `ErpPosting.runPostingWork` reconnect+retry once |
| Pilot readiness | **MISSING** | P0 ≠ 0 (wire flaky, Tx×await, Linux) |

---

## Entry points

| Command | Role |
|---------|------|
| `erp-server` | HTTP + seed |
| `--logic-test` | PO/GR/SO/DL |
| `--finance-test` | finance logic |
| `--manufacturing-test` | mfg logic |
| `--enterprise-test` | platform RBAC/meta/workflow |
| `--integration-test` | logic + integrity + auth smoke (1.6) |
| `--integrity-check` | read-only checker |
| `--version` | metadata |

---

## PILOT_READY

**No.** See `docs/jnative-one-integration-1.6.md` and `docs/erp-release-blockers.md`.
