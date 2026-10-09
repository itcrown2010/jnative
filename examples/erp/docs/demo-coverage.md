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
| AP invoices | `GET /api/erp/finance/ap` | ap_invoices | purchase-chain | ≥1 open remainder |
| Outgoing payments | `GET /api/erp/finance/outgoing-payments` | outgoing_payments | purchase-chain | ≥1 partial |
| Sales orders | `GET /api/erp/salesOrders` | sales_orders | sales-chain | ≥1 |
| Deliveries | `GET /api/erp/deliveries` | deliveries | sales-chain | ≥2 (split) |
| AR invoices | `GET /api/erp/finance/ar` | ar_invoices | sales-chain | ≥1 |
| Incoming payments | `GET /api/erp/finance/incoming-payments` | incoming_payments | sales-chain | ≥1 |
| Inventory balance | `GET /api/erp/inventory` | inventory_balances | opening + GR − DL | matches txns |
| Opening stock | `POST /api/erp/opening/stock*` | opening_stock_* | opening-stock | posted |
| Inventory txns | `GET /api/erp/inventory/transactions` | inventory_transactions | posting | GR/DL/OS rows |
| COA / journals / GL / TB / banks / periods | `/api/erp/finance/*` | finance tables | ErpFinanceCore + posts | seeded + postings |
| Users / roles / settings / audit | `/api/erp/admin/*` | platform | ensurePlatform | present |
| Setup wizard / help / import | existing | — | — | available |

## Hidden (not implemented — not seeded)

Sales quotations/returns · purchase quotations/returns · inventory transfer/count/adjust/batch/serial/valuation · manufacturing execution · CRM · most analytic reports · price lists/currencies.

## Practice path (qty 0)

- Item `FG-PRACTICE-A`, warehouse `WH-PRACTICE` — never touched by showcase postings.
- Suggested tutorial: PO 10 → GR 4+6 → SO deliver 3 → on-hand 7 → AR/AP settle.

## Reset

Admin user menu →「重置演示账套」→ confirm → `POST /api/erp/admin/demo/reset` (DEMO book + `ADMIN.DEMO.RESET` + `ERP_DEMO_MODE=1`). Formal books rejected.
