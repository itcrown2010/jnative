# JNative ERP (JNative One) Phase-1 Validation

**Stamp: `JNATIVE_ERP_PHASE1_PARTIAL`**

**Date:** 2026-10-08  
**Artifact:** `examples/erp`  
**Runtime:** JNative native server + WASM browser target + PostgreSQL

Core purchase→receipt→inventory→sales→delivery loop has been demonstrated on real PostgreSQL (`--logic-test` printed `PASS` with stock **70**, PO received **100/100**, SO delivered **30/30**, four inventory transactions, idempotency keys `gr-1`/`gr-2`/`dl-1`/`dl-2`). Phase-1 is still **PARTIAL** because PostgreSQL wire behavior remains intermittently fragile, extended query is not production-grade, and WASM UI / security / concurrency matrices are not fully closed.

---

## Architecture

Modular monolith under `examples/erp/`:

| Area | Path |
|------|------|
| Shared DTOs / enums | `shared/` |
| HTTP + posting + seed | `server/` |
| WASM shell | `browser/` |
| Migrations | `migrations/001_init.sql`, `001_init_sqlite.sql` |
| Headless acceptance | `tests/ErpLogicTest.java` |

**Stack (as required):** JNative native executable, Browser WASM + WebUI, Typed RPC/HTTP JSON APIs, PostgreSQL, no Spring/JVM server/Node/React/Vue.

Money: integer cents. Quantities: integer pieces.

---

## Database Schema

PostgreSQL DDL in `migrations/001_init.sql`:

- Platform: `companies`, `users`, `roles`, `permissions`, `user_roles`, `role_permissions`, `audit_logs`, `number_sequences`
- Master: `business_partners`, `items`, `warehouses`
- Documents: `purchase_orders` / `_lines`, `sales_orders` / `_lines`, `goods_receipts` / `_lines`, `deliveries` / `_lines`
- Inventory: `inventory_transactions`, `inventory_balances`
- Control: `document_references`, `idempotency_keys`

Company isolation is by `company_id` on business tables.

---

## Business Rules (Phase-1)

- PO/SO: draft → approve → fulfill via GR/DL
- GR: partial receive, reject over-receive, update line `received_qty`, write inventory txn + balance, document reference, idempotency key
- DL: partial delivery, reject over-delivery / insufficient stock, update `delivered_qty`, inventory delta, references, idempotency
- Status refresh: `PARTIALLY_*` / `RECEIVED` / `DELIVERED`
- Replay of same idempotency key must not double-post inventory

---

## Transaction Tests

| Case | Result |
|------|--------|
| PO100 → GR60+40 → stock100 → SO30 → DL10+20 → stock70 | **PASS** (observed on native PG) |
| Idempotent GR replay | **PASS** when scenario completes (key retained, stock unchanged) |
| Over-receive / over-delivery reject | **PASS** when prior steps succeed |
| Sync exception inside `DbTransactionWork` | **Fixed** — rollback now runs before rethrow |
| Concurrent GR/DL / connection drop | **Not closed** (manual stress not completed) |
| Intermittent PG `08P01 invalid message format` | **OPEN** — can fail a second GR inside a long tx |

Workarounds currently in tree:

- PG parameters via simple-query escaped literals (`PgWire.bindQuestions`), not hardened extended Bind
- Hot-path inventory / line qty updates often use literal SQL (avoid flaky bind path)
- After commit/rollback, connection marked broken so pool discards (mitigate wire desync)

---

## Native E2E

```bash
java -jar target/jnative.jar build --project examples/erp
# fresh DB
psql -h 127.0.0.1 -d postgres -c "DROP DATABASE IF EXISTS jnative_erp; CREATE DATABASE jnative_erp OWNER jnative_erp;"
DATABASE_URL='postgresql://jnative_erp@127.0.0.1:5432/jnative_erp' \
  examples/erp/.jnative/build/erp-server --logic-test
# expect: PASS
```

Verified row checks after a green run:

- `inventory_balances.quantity_on_hand = 70`
- `purchase_order_lines.received_qty = 100`
- `sales_order_lines.delivered_qty = 30`
- `inventory_transactions` count = 4
- idempotency keys include `gr-1`, `gr-2`, `dl-1`, `dl-2` (no successful `gr-over`)

SQLite path: usable for migrate smoke; not the Phase-1 acceptance DB.

---

## WASM / UI

- Browser target builds (`examples/erp/.jnative/build/browser/`).
- Shell pages/components exist under `browser/`; **no formal screenshot pack** in this report.
- WebUI DataTable / multi-tab maturity still limited by platform WebUI readiness.

---

## Security Tests

| Item | Status |
|------|--------|
| Password hashing (stdlib `Password`, PBKDF2 iterations raised vs historic 500) | Present for seed users |
| Session cookie login/logout handlers | Present |
| Full RBAC matrix / cross-company denial e2e | **Incomplete** |
| SCRAM PostgreSQL auth | **Unsupported** — local trust/md5 only |

---

## Performance

- Full demo seed (100 items + PBKDF2 users) is slow on native boot; logic-test uses minimal fixtures.
- Discarding pooled PG connections after each tx adds connect overhead (correctness workaround).

---

## Known Issues / JNative Blockers

1. **`JNATIVE_1_0_NOT_READY`** — see `docs/jnative-1.0-readiness.md`, `docs/1.0-release-blockers.md`
2. **PostgreSQL driver**
   - `DbConnection` previously cast backend only to `SQLiteConnection` (**fixed** with `instanceof PgConnection`)
   - `DbByteBuffer.flip()` after `putBytes` zeroed limit (**fixed**)
   - Extended query Bind still not trusted; simple-query literal bind used
   - Intermittent `08P01` under multi-statement transactions
3. **Tx × await / pool** — sync exceptions inside transaction work now roll back; affinity/production isolation still Experimental
4. **WASM WebUI** — not screenshot-certified for ERP chrome
5. **SQLite FFI** — occasional `prepare returned null stmt` under long migrate+DML sequences (not acceptance path)

---

## Final Status

```
JNATIVE_ERP_PHASE1_PARTIAL
```

**STOP** after Phase-1. Not started: GL, production, MRP, advanced CRM, report designer, plugin marketplace.
