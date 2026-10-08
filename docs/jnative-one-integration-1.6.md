# JNative One — Integration Hardening 1.6

**Date:** 2026-10-08  
**Code:** `examples/erp`  
**Host:** Darwin arm64 · `examples/erp/.jnative/build/erp-server`  
**Build:** `java -jar target/jnative.jar build --project examples/erp`  

**Stamp:** `JNATIVE_ONE_1_6_PARTIAL`

Core purchase/sales posting + integrity **PASS** on real Native + PostgreSQL and SQLite.  
P0 remain open (PG wire flaky, Tx×await unproven, Linux deploy missing, concurrency/soak/fault/WASM E2E not verified).  
No new business modules added in this phase.

---

## Actual Implementation Status

| Area | Status | Notes |
|------|--------|-------|
| PO → approve → GR (split) → stock | INTEGRATED | PG + SQLite `--integration-test` |
| SO → approve → DL (split) → stock | INTEGRATED | same; PG `08P01` mitigated by post-tx reconnect + one retry |
| Inventory balance ↔ transactions | TESTED | `ErpIntegrity` |
| Journal debit == credit | TESTED | integrity + finance suite |
| AR/AP invoice → payment → GL | TESTED | `--finance-test` PG + SQLite |
| Manufacturing issue/receipt/WIP | TESTED / flaky | SQLite PASS; PG intermittent `08P01` |
| Enterprise RBAC/meta/workflow | TESTED | PG PASS; SQLite prepare flaky |
| ErpIntegrityChecker | TESTED | read-only; PASS/FAIL prints |
| Concurrency / fault / 30m soak | NOT_VERIFIED | not run |
| Tx × await binding | BLOCKED | sync transaction executor only |
| Browser WASM E2E | NOT_VERIFIED | HTML console RUNNABLE; WASM not INTEGRATED |
| Linux native server | BLOCKED | no ELF |

---

## Integration Test Matrix

Env baseline:

- Native AOT `erp-server` (not JVM mirror)
- PostgreSQL local trust `postgresql://jnative_erp@127.0.0.1:5432/jnative_erp` (migrations pre-applied via `psql`)
- SQLite `sqlite:./build/erp/<name>.db` under repo cwd

| # | Test | Command | Env | Expected | Actual | Result |
|---|------|---------|-----|----------|--------|--------|
| 1 | Purchase/sales + integrity + auth smoke | `…/erp-server --integration-test` | PG fresh DB | PASS integration | PASS integration; stock/journals/PO/SO checks OK | **PASS** |
| 2 | Same | same | SQLite `./build/erp/int-test.db` / `/tmp` variants | PASS | PASS (relative or clean `/tmp`) | **PASS** |
| 3 | Finance AR/AP/payments/TB | `--finance-test` | PG fresh | PASS finance | `FINANCE_CORE … tb=5000` / PASS finance | **PASS** |
| 4 | Finance | `--finance-test` | SQLite `./build/erp/fin-test.db` | PASS | PASS finance | **PASS** |
| 5 | Manufacturing | `--manufacturing-test` | SQLite `./build/erp/mfg-test.db` | PASS | PASS manufacturing | **PASS** |
| 6 | Manufacturing | `--manufacturing-test` | PG fresh | PASS | Intermittent: PASS once earlier; later FAIL `08P01` after `BOM_EXPLODE_OK` | **BLOCKED** (flaky) |
| 7 | Enterprise | `--enterprise-test` | PG fresh | PASS | PASS enterprise | **PASS** |
| 8 | Enterprise | `--enterprise-test` | SQLite | PASS | `prepare returned null stmt` (flaky) | **NOT_VERIFIED** |
| 9 | Concurrency 100× GR/DL | — | — | no negative stock / no double post | not executed | **NOT_VERIFIED** |
| 10 | Idempotency matrix (all posts) | covered partially in logic/finance | PG/SQLite | replay safe | GR/IP replay asserted in suites; not full matrix | **PARTIAL** |
| 11 | Fault injection | — | — | rollback / no double post | not executed | **NOT_VERIFIED** |
| 12 | 30 min soak | — | — | no leak | not executed | **NOT_VERIFIED** |
| 13 | WASM browser E2E | — | Safari/local | login + forms live | HTML console only; WASM not re-certified | **NOT_VERIFIED** |
| 14 | Tx × await same connection | — | PG | proven binding | not proven; sync `blockingWait` used | **BLOCKED** |

---

## Transaction Verification

| Pairing | Evidence | Result |
|---------|----------|--------|
| GR × inventory × JE | logic + finance + integrity journals | **PASS** (suites) |
| DL × inventory × COGS JE | logic + integrity | **PASS** (after 08P01 retry) |
| AP invoice × GL | finance-test | **PASS** |
| AR invoice × GL | finance-test | **PASS** |
| IP/OP × allocation × GL | finance-test | **PASS** |
| Prod issue × inventory × WIP | manufacturing-test | **PASS** SQLite; **BLOCKED** flaky PG |
| Prod receipt × FG × WIP | manufacturing-test | same |

Rollback on mid-tx failure: driver marks connection broken + rollback on error path (`DbLib` `TransactionRollbackRecover`). No dedicated fault-injection suite in 1.6.

---

## Inventory Integrity

Checker: `ErpIntegrity.checkInvBalance` / `checkNoNegativeInventory` / PO·SO fulfillment.

On `--integration-test` (PG + SQLite):

- `PASS integrity:inventory_balance_vs_txn rows=1`
- `PASS integrity:negative_inventory`
- `PASS integrity:po_fulfillment` / `so_fulfillment`

Coverage of adjust/transfer/return: **NOT_VERIFIED** (no dedicated cases in suite).

---

## Accounting Integrity

- Posted journals: `SUM(debit)==SUM(credit)` via integrity + finance `journalsBalanced`
- Money: integer amounts (cents / amount columns) — **Decimal MISSING**
- Trial balance balanced in finance-test (`tb=5000`)

---

## Concurrency

**NOT_VERIFIED** — 100× concurrent GR/DL/reserve/allocation/doc-no not run.

---

## Idempotency

| Path | DB unique key | Suite check |
|------|---------------|-------------|
| GR / DL | `idempotency_keys (company_id, idempotency_key)` | logic replay GR; over-qty reject |
| JE (auto) | same + `je-gr-*` / `je-dl-*` | indirect |
| IP / OP | required Idempotency-Key | finance replay |

Full formal matrix + concurrent duplicate storms: **NOT_VERIFIED**.

---

## Security

| Check | Result |
|-------|--------|
| Cross-company assert smoke | PASS in `--integration-test` |
| Full RBAC denials | PASS `--enterprise-test` on PG |
| Export / report over-permission | covered in enterprise suite (PG) |
| Server-side only | yes (native handlers) |

---

## Native E2E

Headless Native suites above are the E2E evidence. HTTP UI click-path against live server: **NOT_VERIFIED** in this stamp window.

Hardening fix (1.6): `ErpPosting.runPostingWork` — on `08P01` / SQLite prepare-null, **reconnect once and retry** (idempotent posts). PO/SO load still **reconnect after commit** before read.

---

## WASM Verification

| Item | Result |
|------|--------|
| `examples/erp/.jnative/build/browser/` | build artifact present historically |
| Live WASM app in browser | **NOT_VERIFIED** / not INTEGRATED |
| `public/index.html` ASCII console | RUNNABLE (login/nav/tables) |

---

## Performance

30-minute soak with CPU/Memory/GC/connections: **NOT_VERIFIED**.

---

## Known Blockers

| ID | Severity | Issue |
|----|----------|-------|
| B-P0-06 | P0 | Tx × await same physical PG connection unproven |
| B-P0-08P01 | P0 | PostgreSQL wire `08P01` still intermittent (mfg path) |
| B-P0-LINUX | P0 | Linux x86_64 native server not shippable |
| B-P1-WASM | P1 | Browser WASM not integrated for pilot UI |
| B-P1-CONC | P1 | Concurrency / fault / soak not verified |
| B-P2-DECIMAL | P2 | No Decimal type; integer money only |

---

## Open Defects

| ID | Sev | Description | Status |
|----|-----|-------------|--------|
| D-16-01 | P0 | PG `08P01` during manufacturing after BOM explode | OPEN (flaky) |
| D-16-02 | P0 | Tx×await connection binding | OPEN / BLOCKED claim |
| D-16-03 | P1 | SQLite enterprise suite prepare-null flaky | OPEN |
| D-16-04 | P1 | Excessive reconnect can stress SQLite multi-handle | mitigated (retry-only reconnect) |
| D-16-05 | P0 | Sales DL first-post `08P01` before retry harden | FIXED (retry + post-commit reconnect) |

Regression added/updated: diagnostics in `ErpLogicTest` / `ErpFinanceLogicTest`; `--integration-test` harness; integrity checker; posting retry.

---

## Defect triage (1.6)

- **P0 remaining:** wire flaky, Tx×await, Linux → stamp cannot be PASS  
- Prefer fewer features over bad ledger data — no new modules in this phase  

---

## Final acceptance

```
JNATIVE_ONE_1_6_PARTIAL
```

**STOP.** Do not auto-enter next phase.
