# Demo book coverage (seed v2)

**Company:** JNAT / JNative Demo Co (`book_kind=DEMO`)  
**Seed epoch:** 2025-07-01 UTC (`1751328000000`); scenario dates are day offsets from this epoch.  
**Entry:** Login →「体验示例公司」(`POST /api/auth/demo-enter`, requires `ERP_DEMO_MODE=1`).  
**Data path:** SQLite/PG via formal RPC only (no localStorage business data).

## Open pages

| Page | API | Table(s) | Seed scenario | Expected |
|------|-----|----------|---------------|----------|
| Executive dashboard | `GET /api/erp/dashboard` | partners/items/PO/SO | counts after seed | non-zero masters + open docs |
| Customers | `GET /api/erp/partners?kind=CUSTOMER` | business_partners | masters | ≥10 |
| Suppliers | `GET /api/erp/partners?kind=SUPPLIER` | business_partners | masters | ≥8 |
| Items | `GET /api/erp/items` | items | masters | ≥30 + practice SKU |
| Categories | `GET /api/erp/categories` | item_categories | ErpMastersExtra | 3 |
| Units | `GET /api/erp/units` | units_of_measure | ErpMastersExtra | 3 |
| Warehouses | `GET /api/erp/warehouses` | warehouses | masters | ≥3 + WH-PRACTICE |
| Employees | `GET /api/erp/employees` | employees | ErpMastersExtra | 3 |
| Departments | `GET /api/erp/departments` | departments | ErpMastersExtra | ≥4 |
| Purchase requests | `GET /api/erp/purchaseRequests` | purchase_requests | purchase-chain | ≥3 (DRAFT/SUBMITTED/APPROVED) |
| Purchase orders | `GET /api/erp/purchaseOrders` | purchase_orders | purchase-chain | ≥1 |
| Goods receipts | `GET /api/erp/goodsReceipts` | goods_receipts | purchase-chain | ≥2 (split) |
| AP invoices | `GET/POST /api/erp/finance/ap`, `POST .../ap/:id/post` | ap_invoices | purchase-chain | ≥1 open remainder |
| Outgoing payments | `GET/POST /api/erp/finance/outgoing-payments` | outgoing_payments | purchase-chain | ≥1 partial |
| Sales orders | `GET /api/erp/salesOrders` | sales_orders | sales-chain | ≥1 |
| Deliveries | `GET /api/erp/deliveries` | deliveries | sales-chain | ≥2 (split) |
| AR invoices | `GET/POST /api/erp/finance/ar`, `POST .../ar/:id/post` | ar_invoices | sales-chain | ≥1 |
| Incoming payments | `GET/POST /api/erp/finance/incoming-payments` | incoming_payments | sales-chain | ≥1 |
| Inventory balance | `GET /api/erp/inventory` | inventory_balances | opening + GR − DL | matches txns |
| Opening stock | `POST /api/erp/opening/stock*` | opening_stock_* | opening-stock | posted |
| Inventory txns | `GET /api/erp/inventory/transactions` | inventory_transactions | posting | GR/DL/OS rows |
| COA / journals / GL / TB / banks / periods | `/api/erp/finance/*` | finance tables | ErpFinanceCore + posts | seeded + postings |
| Users / roles / settings / audit | `/api/erp/admin/*` | platform | ensurePlatform | present |
| Setup wizard / help / import | existing | — | — | available |

## Purchase → GR → inventory → provisional AP (formal path)

See [purchase-inventory-rules.md](./purchase-inventory-rules.md) and [sales-order-rules.md](./sales-order-rules.md). Money = integer cents; qty = integer. GR/DL post stock + provisional AP/AR. Reservations implemented (`inventory_reservations`; available = on-hand − reserved).

## Inventory costing (moving average)

See [inventory-costing-rules.md](./inventory-costing-rules.md). Perpetual MAV by item×warehouse; sales return restores original DL COGS.

```bash
DATABASE_URL='sqlite:./build/erp/costing-accept.db' \
  examples/erp/.jnative/build/erp-server --costing-test
```

## Finance settlement (sub-ledger)

See [finance-settlement-rules.md](./finance-settlement-rules.md). Receipts/payments + allocation/reverse + bank cash + ageing.

```bash
DATABASE_URL='sqlite:./build/erp/settlement-accept.db' \
  examples/erp/.jnative/build/erp-server --settlement-test
# or: examples/erp/scripts/settlement-accept.sh
```

Evidence (integer cents): AR 1000 SETTLED + AR 600 open 200 after reverse of 200; IP-1 unalloc 200; IP-2 unalloc 100; bank cash movements reconcile to `bank_accounts.balance`. Full GL close / tax / FX / costing out of scope.

## Management GL (accrual, single book)

See [gl-ledger-rules.md](./gl-ledger-rules.md). Policy `MGMT_DIRECT_ACCRUAL` for acceptance; default books remain `PROVISIONAL_CLEARING` (GR clearing). Period lock ≠ P&amp;L/year-end close.

```bash
DATABASE_URL='sqlite:./build/erp/gl-accept.db' \
  examples/erp/.jnative/build/erp-server --gl-test
# or: examples/erp/scripts/gl-accept.sh
```

Evidence: opening 1000 → buy 100 → sell rev80/cogs40 → receipt50 → pay60 → bank990 / AR30 / inv60 / AP40 / equity1000 / profit40; trial + subledger recon; idempotent events; unbalanced rejected; reverse; closed period blocks post.

## Hidden (not implemented — not seeded)

Sales quotations · purchase quotations/returns · inventory transfer/batch/serial · manufacturing execution · CRM · most analytic reports · price lists/multi-currency · formal AP invoice confirmation / full GL close · historical as-of party statement replay · automatic AR↔AP netting.

## Practice path (qty 0)

- Item `FG-PRACTICE-A`, warehouse `WH-PRACTICE` — never touched by showcase postings.
- Suggested tutorial: PO 10 → GR 4+6 → SO deliver 3 → on-hand 7 → AR/AP settle.

## Reset

Admin user menu →「重置演示账套」→ confirm → `POST /api/erp/admin/demo/reset` (DEMO book + `ADMIN.DEMO.RESET` + `ERP_DEMO_MODE=1`). Wipes demo docs/masters, reseeds masters, sets `seed_version=0`, then exits so the process supervisor restarts and boot `applyIfNeeded` restores scenario v2. Formal books rejected.
