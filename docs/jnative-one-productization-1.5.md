# JNative One — Productization & Commercial Readiness 1.5

**Stamp: `JNATIVE_ONE_1_5_BLOCKED`**

**Date:** 2026-10-08  
**Charter:** FEATURE FREEZE — no new CRM/MRP/AI/plugin/microservice/K8s  
**Artifact:** `examples/erp` (+ product scripts/docs)

This phase packages and documents deployability. It does **not** clear platform or ERP P0s. Demonstration on macOS ≠ commercial delivery.

---

## Implemented Features (this phase)

| Area | Deliverable |
|------|-------------|
| Product status matrix | `docs/erp-product-status.md` |
| Release blockers | `docs/erp-release-blockers.md` |
| Config templates | `deploy/jnative-one.conf.example`, env example |
| Runtime config loader | `ErpProductConfig` (env → listen/paths/demo/version) |
| Install | `scripts/jnative-one-install.sh` → `INSTALL_OK` on macOS arm64 host |
| Upgrade script | `scripts/jnative-one-upgrade.sh` (backup → migrate → replace → integrity) |
| Setup wizard (CLI) | `scripts/setup-wizard.sh` + setup lock / concurrent lock |
| Package metadata | `VERSION`, `BUILD_METADATA`, `CHECKSUMS.sha256` |
| Integrity check | `--integrity-check` → `PASS integrity` (empty balances OK) |
| Health | `/health`, `/ready` (+ migration marker), `/version`, `--version` |
| Demo guard | `--reset-demo` refused unless `ERP_DEMO_MODE=1` |
| Structured log (lite) | JSON boot/operation lines without secrets |
| Guides | `user-guide.md`, `admin-guide.md`, `implementation-guide.md` |
| Pilot checklist | `docs/pilot-acceptance-checklist.md` |

---

## Verified Features (evidence this session)

| Check | Result |
|-------|--------|
| Build `examples/erp` | SUCCESS |
| Install script | `INSTALL_OK` prefix `build/jnative-one` |
| `--version` | `1.5.0-productization` / Darwin-arm64 |
| `--integrity-check` on local PG | `PASS integrity` |
| Setup wizard | `SETUP_OK` + lock file |
| `--reset-demo` without demo mode | REFUSED (exit 3) |
| Backup/restore | Previously `BACKUP_RESTORE_OK` (1.4); tool retained |
| Linux x86_64 clean install | **NOT RUN / BLOCKED** |
| Browser WASM business E2E | **NOT VERIFIED** |
| Perf / soak / security / concurrency | **NOT VERIFIED** (no fabricated metrics) |

---

## Unsupported / Out of Scope (freeze)

- New CRM, new MRP features, AI assistant, plugin marketplace, report designer  
- Microservices / Kubernetes  
- Shared-DB SaaS multi-tenant  
- Windows Server as first ship commitment  
- XLSX import  
- Claiming cross-platform without verification  

---

## Native Platform Status

| Target | Status |
|--------|--------|
| macOS arm64 | Dev/demo host — `erp-server` Mach-O arm64 **VERIFIED** locally |
| **Linux x86_64** | **BLOCKED** — epoll PARTIAL stub; platform-status NOT VERIFIED; no ELF product binary produced/tested here |
| Windows Server | Not a v1 commitment |

---

## Security Findings

- Password KDF: PBKDF2 10000 iterations (better than historical 500; still not Argon2id / OWASP-class) → **ERP-P0-04 OPEN**  
- PG SCRAM / `08P01` → **ERP-P0-02 OPEN**  
- Tx×await on PG → **ERP-P0-03 OPEN**  
- Native TLS product edge → **ERP-P0-05 OPEN**  
- OWASP interface suite → **NOT VERIFIED**  
- Demo credentials must not ship as production defaults  

---

## Data Integrity

- Inventory balance vs `inventory_transactions` sum check implemented  
- Journal debit/credit per entry check when lines exist  
- Migration marker `004_enterprise` required  
- Money remains **integer units** (Decimal **MISSING**)  
- Opening inventory controlled GL flow **MISSING**  

---

## Performance Results

**NOT VERIFIED.** No P50/P95/P99 or throughput numbers published.

---

## Backup Restore Results

Tooling: `pg_dump`/`pg_restore` via `scripts/backup-restore.sh`.  
Prior drill: `BACKUP_RESTORE_OK` on developer PostgreSQL.  
Full “stop → wipe → restore → full business check” on Linux pilot host: **NOT VERIFIED**.

---

## Deployment Results

| Step | macOS arm64 | Linux x86_64 |
|------|-------------|--------------|
| Install script | OK | BLOCKED / not executed |
| Migrations via psql | OK | — |
| Service package | layout under `build/jnative-one` | — |
| Clean-room customer VM | NOT VERIFIED | NOT VERIFIED |

---

## Release Blockers

See `docs/erp-release-blockers.md`. **P0 count ≥ 6** including Linux runtime.

Inherited: `JNATIVE_1_0_NOT_READY`.

---

## Pilot Readiness

Checklist: `docs/pilot-acceptance-checklist.md` → **REJECT**.

Cannot mark `PILOT_READY` while:

1. Linux deploy target blocked  
2. P0 ≠ 0  
3. Full native+PG+WASM business E2E and security/concurrency/soak gates incomplete  

---

## Final Conclusion

### `JNATIVE_ONE_1_5_BLOCKED`

Productization artifacts (install/upgrade/setup/config/docs/integrity/demo guards) exist and partially run on macOS+local PostgreSQL. **Commercial / customer pilot is blocked** by native Linux readiness and open data/security P0s.

**STOP.** Do not enter the next phase automatically.
