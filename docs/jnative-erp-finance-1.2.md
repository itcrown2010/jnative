# JNative ERP Finance 1.2

**Stamp: `JNATIVE_ERP_FINANCE_1_2_PARTIAL`**

**Date:** 2026-10-08  
**Artifact:** `examples/erp`  
**Runtime target:** JNative native server + WASM WebUI + PostgreSQL

This is **not** a production-grade finance system. Core posting / AR / AP / TB scaffolding exists and parts of the valuation scenario were observed on SQLite, but PostgreSQL wire instability and incomplete foundation closure keep the release at **PARTIAL**.

---

## Precheck (Foundation)

Checked against:

- `docs/jnative-erp-validation.md` (Phase-1 **PARTIAL**)
- `docs/jnative-erp-foundation-1.1.md` — **MISSING** (P0 documentation gap)

| Area | Status | Notes |
|------|--------|-------|
| Document Engine | Partial | PO/SO/GR/DL loop demonstrated; not full SAP-like doc engine |
| Inventory Engine | Partial | Qty loop OK; cost/value columns + GR/DL valuation added in 1.2 |
| PostgreSQL | **P0 open** | Intermittent `08P01 invalid message format` |
| Decimal | Partial | Money = integer units (no float); not full Decimal lib surface |
| Transaction | Partial | Sync tx + rollback path fixed earlier; still fragile under PG |
| Company Isolation | Partial | `company_id` on business tables |
| Authorization | Thin | Session + role string; not fine-grained finance RBAC |

**P0 blockers (do not claim production finance):**

1. Missing `docs/jnative-erp-foundation-1.1.md`
2. PostgreSQL wire / extended-query fragility (`08P01`)
3. SQLite driver intermittent `prepare returned null stmt` / `incomplete input` under long finance workflows
4. Thin RBAC / audit for period reopen
5. Concurrent allocation matrix not closed on native PG

---

## Schema

Migrations:

- `examples/erp/migrations/002_finance.sql` (PostgreSQL)
- `examples/erp/migrations/002_finance_sqlite.sql` (SQLite)
- Inventory cost columns: `items.unit_cost_amount`, `inventory_balances.value_amount`, txn value fields

Tables: `gl_accounts`, `fiscal_periods`, `account_mappings`, `journal_entries`, `journal_entry_lines`, `bank_accounts`, `ar_invoices` (+ lines), `incoming_payments` (+ allocations), `ap_invoices` (+ lines), `outgoing_payments` (+ allocations).

Account mapping keys (configurable, not hardcoded IDs in rules): `INVENTORY`, `GR_CLEARING`, `COGS`, `REVENUE`, `AR`, `AP`, `BANK`.

---

## Accounting Rules

- Journal: `SUM(debit) == SUM(credit)`; reject empty / negative / dual-sided lines / inactive accounts / closed periods
- Lifecycle statuses: `DRAFT` / `POSTED` / `REVERSED` (reverse path stubbed via status; full reverse JE helper not fully productized)
- Posting engine: `PostingRequest` → validate → persist JE + lines → idempotency key
- Rules: `GoodsReceiptPosting`, `DeliveryCostPosting`, `ARInvoicePosting`, `APInvoicePosting`, `IncomingPaymentPosting`, `OutgoingPaymentPosting`
- GR valuation: Dr Inventory / Cr GR-clearing  
- Delivery COGS: Dr COGS / Cr Inventory (moving average)  
- AR invoice: Dr AR / Cr Revenue  
- AP invoice: Dr GR-clearing / Cr AP  
- Incoming payment: Dr Bank / Cr AR + allocations  
- Outgoing payment: Dr AP / Cr Bank + allocations  

---

## Implementation Map

| Area | Path |
|------|------|
| COA / JE / PostingEngine | `server/ErpFinanceCore.java` |
| AR / AP / Payments | `server/ErpFinanceArAp.java` |
| TB / GL / Ageing | `server/ErpFinanceReports.java` |
| Inventory cost | `server/ErpInventory.java` + GR/DL hooks in `ErpPosting.java` |
| HTTP APIs | `/api/erp/finance/*` in `ErpServer.java` |
| WASM Finance menu | `browser/ErpApp.java` |
| Native headless | `--finance-test` → `ErpFinanceLogicTest` |

---

## Journal / Inventory Tests

Scenario target:

- Buy 100 @ 50 → value 5000  
- Sell 30 @ 80 → revenue 2400  
- Stock 70 / value 3500  
- COGS 1500 / GP 900  

**Observed (SQLite native, intermittent):** after GR+DL, inventory **70 / 3500** and COGS account net **1500** printed successfully in instrumented runs. Full AR/AP/payment/TB chain did **not** reliably complete due to SQLite prepare failures mid-workflow.

**Observed (PostgreSQL native):** often fails with `08P01` after GR or during subsequent SO/DL/finance steps even when schema is pre-applied via `psql`.

---

## AR / AP / Payment Tests

Implemented in code + logic-test harness:

- AR 2400 → collect 1000 + 1400 → open 0  
- AP 5000 → pay 2000 + 3000 → open 0  
- Idempotent payment replay  
- Over-allocation reject  
- Closed-period JE reject  

**Native end-to-end status:** not stably green on PG or SQLite in this pass → **PARTIAL**.

---

## Concurrency

- Allocation path uses `SELECT … FOR UPDATE` on PostgreSQL invoice rows + optimistic `open_amount` predicate on update  
- Dual 700-vs-1000 stress **not** completed as a native automated gate

---

## Native E2E

```bash
java -jar target/jnative.jar build --project examples/erp
# optional: apply SQL via psql for cleaner schema
DATABASE_URL='postgresql://jnative_erp@127.0.0.1:5432/jnative_erp' \
  examples/erp/.jnative/build/erp-server --finance-test
```

Must remain native executable (no JVM mirror substitute). Current native runs: **unstable**.

---

## UI Verification

Finance menu entries present in WASM shell:

Chart of Accounts, Journal Entries, AR/AP Invoices, Incoming/Outgoing Payments, Bank Accounts, Trial Balance, General Ledger, AR/AP Ageing.

List APIs under `/api/erp/finance/*` (session required). Full Design System amount formatting / picker UX is **stub-level** (menu + GET binding hints), not a polished ERP UI.

---

## Known Issues

1. PG `08P01` remains a release blocker for claiming PASS  
2. SQLite prepare/`incomplete input` under multi-step finance  
3. Migration script runner can stall mid-002 on PG wire; `execRetry` + `ensureFinanceArApTables` mitigate but are not a hardened migrator  
4. Period reopen audit / authorization incomplete  
5. Journal reversal product path incomplete  
6. Concurrent payment allocation not certified  
7. Foundation 1.1 doc absent  
8. Do **not** treat this as production finance

---

## Stamp

`JNATIVE_ERP_FINANCE_1_2_PARTIAL`

STOP — do not auto-start production management module.
