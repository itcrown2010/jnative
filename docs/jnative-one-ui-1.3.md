# JNative One — UI 1.3 Enterprise Data Grid & Query Center

作者：张晓达

**Date:** 2026-10-08  
**Principles:** UI FIRST · Mock Data allowed · No new ERP backend modules  
**Scope:** Unified EnterpriseDataGrid, advanced filters, saved views, drill-down, global search  

**Stamp:** `JNATIVE_ONE_UI_1_3_PASS`

---

## Verdict

EnterpriseDataGrid is mounted on the required list pages and verified in real Chromium (`examples/erp/scripts/ui-1.3-verify.mjs` → `public/screenshots/ui13-verify.json`: **23 OK / 0 FAIL**). Advanced Filter, Saved Views, Column Manager, grouping, page-vs-all selection, CSV export, Global Search, keyboard row nav, and measured large-dataset query (up to 100k rows with page-sized DOM) all passed.

JNative Browser WASM SPA remains **BLOCKED**; acceptance is the served `examples/erp/public/` SPA.

---

## Architecture

```
examples/erp/public/js/components/
  grid/
    model.js      GridColumnDefinition, GridQueryState, operators by type
    filter.js     Filter eval, date presets, stable sort, aggregates
    provider.js   MockGridDataProvider + RpcGridDataProvider stub
    grid.js       EnterpriseDataGrid mount (toolbar/header/body/pager)
  query/
    filter-builder.js   FilterBuilder modal (AND/OR nested)
    saved-views.js      SavedViewManager (localStorage DEMO)
  nav/
    business.js         BusinessNavigationService + WorkspaceTabs
    global-search.js    GlobalSearch categorized DEMO index
```

`browser/components/grid|query/` hold architectural stubs + README mapping to public runtime.

Reuse: AppShell, WorkspaceTabs, Modal, DesignTokens, MockDataProvider, PageRegistry, Document Editor (UI 1.2) for Open drill targets.

---

## Grid Components

| Capability | Behavior |
|------------|----------|
| Columns | Strong typed `STRING/INTEGER/DECIMAL/DATE/DATETIME/BOOLEAN/ENUM/MONEY/QUANTITY` |
| Sort | Asc / desc / clear; multi-column; **stable** (original index tie-break) |
| Page size | 10 / 20 / 50 / 100; shows total, page, page count |
| Pin | Left pin first column; right sticky Actions |
| Resize | Drag handle; minWidth / maxWidth clamped |
| Column Manager | Show/hide, reorder, restore defaults |
| Selection | Page select vs **ALL filtered results** — labels never confuse the two |
| Batch toolbar | Export, batch open (≤5 DEMO tabs), clear — **no formal posting** |
| Grouping | Single-level group-by chips |
| Aggregates | COUNT / SUM / AVG / MIN / MAX with type guards; MONEY via integer cents |
| States | Loading / error / empty |
| Keyboard | Arrow / Enter / Escape / Space (IME-safe) |
| Context menu | Open / drill / toggle select |
| CSV | UTF-8 BOM, quoting, formula-injection prefix `'` |

---

## Query Model

`GridQueryState`: `{ filters, sorting, page, pageSize, grouping, columns, selectScope }`.

Filter operators: EQ NE GT GTE LT LTE BETWEEN CONTAINS STARTS_WITH IN IS_NULL IS_NOT_NULL — restricted by `dataType`.

Logic: AND / OR nested groups; max depth **3**, max conditions **20**.

Date presets (local calendar day semantics, documented as DEMO / typically Asia/Shanghai machines): today, yesterday, this week/month/quarter/year, last month, custom range.

Money compare uses **integer cents** (`JnGridModel.moneyToCents`) — no float equality.

---

## Saved Views

`SavedView`: id, pageId, name, filters, sorting, visibleColumns, columnOrder, columnWidths, pageSize, isDefault.

Persistence: **browser localStorage** key `jn_saved_views_v13` — explicitly DEMO personal layout, **not** formal user-config database.

Actions: create, rename, delete, set default, apply, restore layout defaults.

---

## Data Interface

| Provider | Status |
|----------|--------|
| `MockGridDataProvider` | Active — in-memory filter/sort/page/aggregate; optional synthetic datasets for perf |
| `RpcGridDataProvider` | Stub — rejects with clear “not connected” (no forged server success) |

---

## Business Navigation

| From | Drill |
|------|--------|
| Customer | Customer detail (editor) |
| Sales Order | SO editor |
| Purchase Order | PO editor |
| Inventory txn | Source doc filter (SO/PO/GR/DL ref) |
| AR / AP | Related SO / PO via references or search |

Opens target via **WorkspaceTabs** (`JnApp.navigate`) — does not silently replace without a tab.

---

## Global Search

Categories: Customers, Suppliers, Items, Sales Orders, Purchase Orders.  
Keyword → categorized DEMO hits → click opens page + record.  
Not an enterprise search server.

---

## Pages wired (`enterpriseGrid: true`)

Customers, Suppliers, Items, Sales Orders, Purchase Orders, Inventory Balance, Inventory Transactions, Accounts Receivable (`fin.ar`), Accounts Payable (`fin.ap`), Production Orders.

All share GridToolbar / FilterBar / EnterpriseDataGrid / Pagination / SavedViewManager chrome.

---

## Performance (measured)

From `ui13-verify.json` on this machine (Chromium). **Never render 100k DOM rows** — pageSize cap (50 in probe / ≤100 in UI).

| n | queryMs | pageRows | strategy |
|--:|--------:|---------:|----------|
| 100 | 0 | 50 | memory-paging |
| 1,000 | 0 | 50 | memory-paging |
| 10,000 | 1 | 50 | memory-paging + no full DOM |
| 100,000 | 13 | 50 | memory-paging + no full DOM |

Mount probe (1000 synth, pageSize 20): `mountMs=82`, `scrollMs=0`.  
`performance.memory` may be unavailable depending on Chromium flags — recorded when present.

---

## Browser Verification

| Item | Result |
|------|--------|
| Runtime | Playwright Chromium |
| URL | `http://127.0.0.1:18700/` |
| Script | `examples/erp/scripts/ui-1.3-verify.mjs` |
| Viewports | 1366×768, 1440×900, 1920×1080 |
| WASM SPA | BLOCKED (not acceptance surface) |

---

## Screenshots

`examples/erp/public/screenshots/`

| File | Subject |
|------|---------|
| `ui13-customer-grid.png` | Customer Grid |
| `ui13-sales-order-grid.png` | Sales Order Grid |
| `ui13-advanced-filter.png` | Advanced Filter |
| `ui13-saved-views.png` | Saved Views |
| `ui13-column-manager.png` | Column Manager |
| `ui13-inventory-grid.png` | Inventory Grid |
| `ui13-ar-grid.png` | AR Grid |
| `ui13-global-search.png` | Global Search |
| `ui13-viewport-*.png` | Viewport checks |

---

## Known Issues

1. WASM SPA still BLOCKED — browser Java stubs are documentation only.
2. Virtual scroll not implemented; large sets use **memory paging** (sufficient for 100k query tests without DOM blow-up).
3. Batch “select all results” is a UI selection scope flag; export-filtered caps at 10k rows for safety.
4. Global Search debounce + panel may overlap WorkspaceTabs on narrow widths — still usable at 1366.
5. Multi-column sort UI is click-cycle per column (order = click sequence); no dedicated sort panel yet.

---

## Acceptance Checklist

| Criterion | Status |
|-----------|--------|
| Unified table | PASS |
| Advanced filter | PASS |
| Saved views | PASS |
| Group + aggregate | PASS |
| Business drill | PASS |
| Global search | PASS |
| Real browser verify | PASS |

**Stamp: `JNATIVE_ONE_UI_1_3_PASS`**

STOP — do not auto-start Backend or the next UI phase.
