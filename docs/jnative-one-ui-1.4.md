# JNative One — UI 1.4 Full ERP UI Coverage & Interactive Demo

**Date:** 2026-10-08  
**Principles:** UI FIRST · Unified DemoStateStore · No new formal ERP backend modules  
**Stamp:** `JNATIVE_ONE_UI_1_4_PASS`

---

## Verdict

All **85** PageRegistry routes load in real Chromium without blank/error pages. Demo business mutations go through **ApplicationService → DemoStateStore** (events for UI only). Five golden checks passed: purchase chain, sales chain, inventory 100→70, finance margin 900 under no-tax assumptions, balanced journals. KPIs read from DemoStateStore (not hardcoded).

WASM SPA remains **BLOCKED** as the product shell; acceptance is `examples/erp/public/` SPA.

Artifact: `examples/erp/public/screenshots/ui14-verify.json` (25 OK / 0 FAIL).  
Coverage: `examples/erp/public/screenshots/ui14-page-coverage.json`.

---

## Architecture

```
public/js/demo/
  events.js     DemoBusinessEvent bus (subscribe/emit/history)
  seed.js       DemoSeedService (1 company, 20/10/100/3/10 masters)
  store.js      DemoStateStore + localStorage jn_demo_state_v14
  services.js   ApplicationService (only mutation path for cross-module ops)

public/js/data/mock.js   Thin read/write facade over DemoStateStore
```

Pages / grids / document editor call `JnAppService` (or `JnData` which routes masters/orders to it). Direct cross-module writes from page templates are forbidden by convention and golden-path tests.

---

## Page Coverage

| Metric | Value |
|--------|------:|
| Registered pages | 85 |
| Loaded OK | 85 |
| Modules | Workbench, Master Data, Sales, Purchasing, Inventory, Finance, Manufacturing, CRM, Reports, Administration |

Each page has: `pageId`, `module`, `route`, `title`, `template`, `status` (`PREVIEW` \| `INTERACTIVE` \| `VERIFIED`), `permissions`.

Core interactive surfaces marked **VERIFIED** after this run (dashboard, customers, SO/PO, inventory, AR/AP, journals, BOM editor, MRP, settings). Report templates remain **PREVIEW** where they are read-oriented slices.

Full matrix: `ui14-page-coverage.json`.

---

## DemoStateStore

Collections include: Customers, Suppliers, Items, Warehouses, SalesOrders, PurchaseOrders, Deliveries, GoodsReceipts, InventoryTransactions, InventoryBalances, ARInvoices, APInvoices, Payments (incoming/outgoing), JournalEntries, ProductionOrders, plus CRM/system masters.

**Persistence**

| Behavior | Detail |
|----------|--------|
| Storage | `localStorage` key `jn_demo_state_v14` |
| Refresh | **Keeps** demo state |
| Reset Demo Data | Confirm → re-seed → rewrite localStorage |
| Clear site data | Returns to seed on next load |
| Server DB | **Never** claimed |

Top banner always shows **DEMO MODE**.

---

## DemoBusinessEvent

Types include: `CUSTOMER_CREATED`, `ITEM_CREATED`, `PURCHASE_ORDER_CREATED`, `GOODS_RECEIPT_POSTED`, `SALES_ORDER_CREATED`, `DELIVERY_POSTED`, `AR_INVOICE_CREATED`, `AP_INVOICE_CREATED`, `PAYMENT_RECORDED`, `PRODUCTION_ISSUED`, `PRODUCTION_RECEIVED`, `DEMO_RESET`, `STATE_CHANGED`, …

Events update subscribers / audit trail in UI. **Not** a substitute for real DB transactions.

---

## Business Flows

| Flow | ApplicationService path |
|------|-------------------------|
| Purchasing | save PO → approve → `postGoodsReceipt` → `createAPInvoiceFromGR` → `recordOutgoingPayment` |
| Sales | save SO → approve → `postDelivery` → `createARInvoiceFromDelivery` → `recordIncomingPayment` |
| Inventory | GR / Delivery / `transferInventory` / `adjustInventory` → txn + balance rules |
| Finance | Balanced `postJournal`; AR/AP/Bank/COGS/Revenue accounts |
| Manufacturing | BOM + MO → `issueProduction` / `receiveProduction` |
| CRM | Leads / Opps / Visits in store (status via master save) |

Document editor **Save / Submit / Approve** routes through ApplicationService (`approve` on Delivery/GR posts stock).

---

## Data Consistency (golden)

Assumptions: **no tax, no freight, no discount, FG-DEMO-A opening qty 0**.

| Step | Expected | Measured |
|------|----------|----------|
| GR 100 × 50 | stock 100 | 100 |
| Delivery 30 × 80 | stock 70 | 70 |
| Stock value | 70 × 50 = 3500 | 3500 |
| Revenue | 2400 | 2400 |
| COGS | 1500 | 1500 |
| Gross margin | 900 | 900 |
| Journals | debit = credit | all balanced |
| Traceability | txn refs GR/DL | `GR-5301:+100`, `DL-3001:-30` |

---

## Navigation

WorkspaceTabs + PageRegistry unchanged; GlobalSearch (UI 1.3) and BusinessNavigation drill use the same store. Reset control in banner and System Settings.

---

## Browser Verification

| Item | Result |
|------|--------|
| Runtime | Playwright Chromium |
| Coverage | 85/85 |
| Golden chains | PASS |
| Viewports | 1366 / 1440 / 1920 |
| WASM SPA | **BLOCKED** (documented; not acceptance surface) |

---

## Screenshots

Under `examples/erp/public/screenshots/`:

- `ui14-executive-dashboard.png`
- `ui14-customer-list.png`
- `ui14-sales-order-editor.png`
- `ui14-purchase-order-editor.png`
- `ui14-inventory-balance.png`
- `ui14-inventory-transactions.png`
- `ui14-ar-invoice.png`
- `ui14-ap-invoice.png`
- `ui14-journal-entry.png`
- `ui14-bom-editor.png`
- `ui14-mrp-workbench.png`
- `ui14-system-settings.png`
- `ui14-viewport-*.png`

---

## Known Issues

1. WASM Browser SPA still BLOCKED — stubs only under `browser/`.
2. Tax / freight / multi-currency revaluation / landed cost **not supported** — explicitly DEMO assumptions.
3. Moving-average cost on receipts; golden path uses single receipt cost 50.
4. Report pages are PREVIEW slices over DemoStateStore, not a BI engine.
5. Concurrent multi-tab edits can race on localStorage last-write-wins.

---

## Acceptance Checklist

| Criterion | Status |
|-----------|--------|
| All planned pages accessible | PASS (85/85) |
| Core pages interactive | PASS |
| Five demo chains | PASS |
| Demo data consistent | PASS |
| Real browser verification | PASS |

**Stamp: `JNATIVE_ONE_UI_1_4_PASS`**

STOP — do not auto-start Backend.
