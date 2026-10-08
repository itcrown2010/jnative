# JNative One ERP — Product Status Matrix (1.5)

**Audit date:** 2026-10-08  
**Scope:** `examples/erp` only (no other ERP product tree found under `examples/`)  
**Host evidence:** macOS arm64 (Darwin) — `erp-server` is Mach-O arm64  
**Rule:** Report files ≠ acceptance. Levels require cited evidence.

Levels: **MISSING** · **IMPLEMENTED** · **RUNNABLE** · **TESTED** · **PILOT_READY**

---

## Prior stage stamps (as written — not re-certified here)

| Stage | Document stamp | Re-audit note |
|-------|----------------|---------------|
| Phase-1 | `JNATIVE_ERP_PHASE1_PARTIAL` | PG loop observed; wire flaky |
| Finance 1.2 | `PARTIAL` | Posting exists; not full finance product |
| Manufacturing 1.3 | `PASS` (doc) | Not re-run in 1.5 freeze |
| Enterprise 1.4 | `PARTIAL` | `--enterprise-test` once PASS; re-run flaky |
| Platform 1.0 | `JNATIVE_1_0_NOT_READY` | 8 P0s in `1.0-release-blockers.md` |

---

## Capability matrix

| Feature | Level | Evidence |
|---------|-------|----------|
| Native server binary (macOS arm64) | **TESTED** | `file examples/erp/.jnative/build/erp-server` → Mach-O arm64; builds via `jnative.jar build --project examples/erp` |
| Native server Linux x86_64 | **MISSING** / **BLOCKED** | No Linux binary in tree; `jnative_poll_epoll.c` PARTIAL stub; `platform-status.md` Linux x64 **NOT VERIFIED** |
| Browser WASM shell | **RUNNABLE** | `examples/erp/.jnative/build/browser/app.wasm` builds; no Chromium E2E matrix in 1.5 |
| PostgreSQL connect (trust/local) | **TESTED** (intermittent) | Logic/enterprise tests on PG; `08P01` storms documented |
| PostgreSQL SCRAM auth | **MISSING** | `PgDriverLib` SCRAM not supported (1.0 blocker B-P0-05) |
| Purchase→GR→SO→DL loop | **TESTED** | `--logic-test` PASS observed historically on PG |
| Finance GL/AR/AP | **TESTED** (partial scope) | `--finance-test` / finance docs PARTIAL |
| Manufacturing BOM/WIP/MRP | **TESTED** (doc PASS) | `--manufacturing-test`; not re-run 1.5 |
| RBAC MODULE.RESOURCE.ACTION | **TESTED** | `ErpPlatformAuth` + `--enterprise-test` OK auth |
| Company isolation | **TESTED** | enterprise isolation cases |
| Custom fields / forms meta | **TESTED** | enterprise suite |
| Sequential workflow | **TESTED** | enterprise suite; not all HTTP approve paths wired |
| Safe report datasets | **TESTED** | enterprise suite |
| CSV import/export | **TESTED** | partners CSV + formula harden; XLSX **MISSING** |
| Audit events | **IMPLEMENTED** / **TESTED** | table + enterprise write/count; not full op coverage |
| Settings / config center | **IMPLEMENTED** | `system_settings`; file-based product config added in 1.5 |
| `/health` `/ready` | **RUNNABLE** | handlers present; ready checks DB; 1.5 adds migration version field |
| Installer (Linux package) | **IMPLEMENTED** (scripts) | `scripts/jnative-one-install.sh` — **not** verified on Linux host |
| Upgrade tool | **IMPLEMENTED** (scripts) | `scripts/jnative-one-upgrade.sh` — upgrade path **NOT VERIFIED** end-to-end on old→new DB fixture |
| Backup / restore | **TESTED** (macOS+local PG) | `scripts/backup-restore.sh` → `BACKUP_RESTORE_OK` in 1.4 |
| Setup wizard | **IMPLEMENTED** (CLI) | `scripts/setup-wizard.sh` — not full WebUI wizard |
| Demo Trading Company seed | **RUNNABLE** | `ErpSeed` JNAT demo; DEMO_MODE guards in 1.5 |
| Opening inventory controlled posting | **MISSING** | qty/cost via inventory engine only; no dedicated opening GL flow |
| Decimal money type | **MISSING** | integer units throughout |
| Integrity check tool | **IMPLEMENTED** / **TESTED** | `--integrity-check` on PG in 1.5 session |
| Structured logging | **IMPLEMENTED** (lite) | request-scoped log lines; not full shipping pipeline |
| Security test suite (OWASP matrix) | **MISSING** | no dedicated security e2e suite executed in 1.5 |
| Concurrency matrix | **MISSING** | **NOT VERIFIED** |
| Performance 100k scale | **MISSING** | **NOT VERIFIED** — no fabricated numbers |
| 30 min soak | **MISSING** | **NOT VERIFIED** |
| Full business E2E + WASM browser | **MISSING** | headless logic tests ≠ browser E2E |
| UI viewport 1440/1920/1366 | **MISSING** | **NOT VERIFIED** |
| User / admin / implementation guides | **IMPLEMENTED** | docs generated in 1.5 |
| Pilot acceptance | **MISSING** | P0 ≠ 0; Linux blocked |

---

## Other ERP directories

Searched `examples/` — only `examples/erp`. Scratch/diagnostics under `build/erp/` and `examples/erp/tmp/` are **not** product trees.

---

## Pilot readiness (summary)

**Not PILOT_READY.** Primary deploy target Linux x86_64 is blocked; platform and ERP P0s remain. See `docs/erp-release-blockers.md`.
