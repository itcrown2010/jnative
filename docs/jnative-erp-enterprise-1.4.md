# JNative ERP Enterprise Foundation 1.4

作者：张晓达

**Stamp: `JNATIVE_ERP_ENTERPRISE_1_4_PARTIAL`**

**Date:** 2026-10-08  
**Artifact:** `examples/erp`  
**Runtime:** JNative native executable + Browser WASM shell + PostgreSQL

Enterprise platform layer (RBAC, custom fields, forms metadata, sequential workflow, safe reports, CSV IO, settings, audit, deploy/health/backup) landed as a **modular monolith** under `examples/erp`. Native `--enterprise-test` reached **`PASS enterprise`** on PostgreSQL (fresh DB + psql-applied 001–004). Re-runs remain **flaky** under known PG wire `08P01`. This does **not** clear `JNATIVE_1_0_NOT_READY`.

---

## Precheck

| Source | Status |
|--------|--------|
| ERP Phase-1 | `PARTIAL` |
| Foundation 1.1 | Doc missing / capabilities partial |
| Finance 1.2 | `PARTIAL` |
| Manufacturing 1.3 | `PASS` on PG (documented) |
| JNative 1.0 readiness | **`JNATIVE_1_0_NOT_READY`** — 8 P0s in `docs/1.0-release-blockers.md` |

**Platform areas checked (not assumed PASS):** Compiler, Native Runtime, Browser WASM, WebUI, Typed RPC, PostgreSQL, Transaction, JSON, Filesystem, Security.

**Carried P0s (not masked by ERP workarounds for data integrity):**

1. PostgreSQL SCRAM / wire `08P01 invalid message format` (B-P0-05 + observed migrate/query storms)
2. Transaction integrity across await unproven (B-P0-06)
3. Password KDF iterations insecure default (B-P0-04)
4. Multi-platform CI absent; Windows/Linux EventLoop incomplete

Migrate path skips re-DDL when `companies` / `platform_permissions` already exist and skips statements after one 08P01 reconnect — **operational hygiene**, not a claim that PG wire is fixed.

---

## Architecture

Modular monolith. **No** microservices, **no** eval / arbitrary user code, **no** arbitrary SQL API, **no** uncontrolled reflection.

| Area | Location |
|------|----------|
| Schema | `migrations/004_enterprise.sql` (+ sqlite mirror) |
| Authorization | `server/ErpPlatformAuth.java` |
| Metadata / custom fields / forms | `server/ErpPlatformMeta.java` |
| Workflow | `server/ErpPlatformWorkflow.java` |
| Reports | `server/ErpPlatformReports.java` |
| Import / export | `server/ErpPlatformIo.java` |
| Config + audit | `server/ErpPlatformConfig.java` |
| Tests | `tests/ErpEnterpriseLogicTest.java` via `--enterprise-test` |
| Admin APIs | `/api/erp/admin/*` in `ErpServer.java` |
| UI | Administration menu in `browser/ErpApp.java` |
| Deploy | `deploy/env.example`, `/health`, `/ready` |
| Backup | `scripts/backup-restore.sh` (`pg_dump` / `pg_restore`) |

Logical modules map to charter `server/platform/*` concerns inside the existing ERP server package (single process).

---

## Authorization Model

- **Permission** codes: `MODULE.RESOURCE.ACTION` (e.g. `SALES.ORDER.READ`, `SALES.ORDER.APPROVE`, `ADMIN.AUDIT.READ`)
- **Role** / **RolePermission** / **UserRole** tables: `platform_*`
- **DataScope:** `OWN` | `DEPARTMENT` | `COMPANY` via `ErpPlatformAuth.maxDataScope` / `canAccessOwner`
- **Company isolation:** `assertSameCompany`; cross-company access denied unless explicit future cross-company process
- Server re-checks permissions on admin APIs (`ErpAdminGate`); UI hide is not authorization

**Denial tests covered:** other-owner read under OWN, sales posting finance, purchase approving SO, sales inventory, admin of A accessing company B roles.

---

## Metadata Model

- `CustomFieldDefinition` / `CustomFieldValue` with types STRING, INTEGER, DECIMAL, BOOLEAN, DATE, DATETIME, ENUM
- Validation: required, length, integer/decimal-units, boolean, date epoch, enum allow-list; safe field codes only
- Values stored in typed `custom_field_values` table (core columns remain strongly typed)
- Forms: `FormDefinition` + `FormFieldDefinition` (visible / readonly / required / order / group)
- Submission path rejects unsafe codes; readonly/hidden must be re-validated server-side (callers)

---

## Workflow Model

- Definitions + **versioned** snapshots (`workflow_versions`); running instances bind `workflow_version_id`
- Statuses: DRAFT / SUBMITTED / IN_APPROVAL / APPROVED / REJECTED / CANCELLED
- v1: **sequential** steps; role-gated act; amount snapshot rejects mid-approval amount change
- Duplicate act rejected; overreach rejected
- Approval success returns code for caller to invoke **business transition** — workflow does not UPDATE sales/PO rows directly

---

## Reporting Model

Predefined datasets only:

- `InventoryBalanceDataset`
- `SalesOrderDataset`
- `PurchaseOrderDataset`
- `CustomerBalanceDataset`

Gated by `ADMIN.REPORT.RUN` + `company_id`. No arbitrary SQL. Filter/sort/page/aggregate beyond LIMIT is thin in v1.

---

## Import / Export

- **CSV** import partners (upload→parse→validate→import→error report); row/size/type limits; permission `ADMIN.IMPORT.RUN`
- **CSV** export with UTF-8, quoting, and formula-injection hardening (`=`, `+`, `-`, `@` prefixed with `'`)
- **XLSX:** not claimed (no mature parser in tree)

---

## Configuration & Audit

- `system_settings` + `setting_history`; types STRING/INTEGER/DECIMAL/BOOLEAN/DATE/ENUM
- Sensitive values display as `***`; audit redacts password/secret/token payloads
- `audit_events` append-only for business users (`tryDeleteAsUser` always 0)

---

## Deployment

Single-enterprise process, multi-company books:

- Native server + PostgreSQL + static WASM assets + local storage path (`deploy/env.example`)
- `/health` liveness; `/ready` checks DB dependency
- Versioned migrations `001`–`004` with `schema_migrations` tracking + idempotent record
- Backup/restore: **`pg_dump --format=custom`** + **`pg_restore`** (`scripts/backup-restore.sh`)

**Backup drill (observed):** `BACKUP_RESTORE_OK` — dump created, restore DB created, companies/users/`004_enterprise` present.

---

## Security

| Control | Status |
|---------|--------|
| RBAC + data scope | Implemented (server) |
| Company isolation | Implemented |
| No eval / no arbitrary SQL | Enforced by design |
| CSV formula injection | Hardened on export |
| Sensitive settings | Masked |
| Audit immutability | Soft (API refuses delete) |
| Platform P0 crypto/PG | **Open** — inherited |

---

## Tests

| Suite | Result |
|-------|--------|
| Authorization denials | OK |
| Company isolation | OK |
| Custom fields | OK |
| Forms | OK |
| Workflow (overreach, amount change, duplicate, version freeze) | OK |
| Reports (deny + safe dataset + unknown reject) | OK |
| Import/export | OK |
| Config/audit | OK |
| Migration track | OK |
| Native `--enterprise-test` (fresh PG) | **`PASS enterprise`** (once) |
| Re-run same binary | Flaky `08P01` mid-suite |
| Backup restore script | **OK** |
| 100 users / 100k rows / 10k report&import scale | **Not run** (targets, not claims) |
| UI 1440/1920/1366 matrix | Menu present; **not** automated viewport pass |

```bash
java -jar target/jnative.jar build --project examples/erp
psql … apply 001/002/003/004
DATABASE_URL='postgresql://jnative_erp@127.0.0.1:5432/jnative_erp' \
  examples/erp/.jnative/build/erp-server --enterprise-test
# → PASS enterprise
```

---

## Native E2E

- Binary: `examples/erp/.jnative/build/erp-server`
- WASM: `examples/erp/.jnative/build/browser/app.wasm` (Administration menu compiled)
- Not JVM host mirror

---

## Known Blockers

1. `JNATIVE_1_0_NOT_READY` platform P0s (especially PG `08P01`)
2. Enterprise suite not soak-stable across reconnect-heavy re-runs
3. Performance scale matrix not executed
4. Full FormRenderer / Design System components are lite (button menu + JSON APIs), not pixel-validated viewports
5. XLSX unsupported
6. Workflow not wired into every sales/PO HTTP approve path yet (engine + tests exist; domain hooks incremental)

---

## Verdict

**`JNATIVE_ERP_ENTERPRISE_1_4_PARTIAL`**

Real native PG evidence for core enterprise logic + CSV IO + audit/config + `pg_dump` restore drill + WASM admin menu. Not a production enterprise platform stamp while platform P0s and UI/perf gaps remain.

**STOP.** Do not auto-enter next phase.
