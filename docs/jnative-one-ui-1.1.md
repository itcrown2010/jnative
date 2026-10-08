# JNative One — UI 1.1

**Date:** 2026-10-08  
**Principles:** UI FIRST · COMPONENT FIRST · DESIGN SYSTEM FIRST  
**Scope:** Enterprise ERP Web UI only — no new ERP backend business logic  

**Stamp:** `JNATIVE_ONE_UI_1_1_PASS`

---

## Verdict

All **85** registered routes load in a real browser with unified DesignTokens / AppShell, MockDataProvider DEMO interactions, and Playwright smoke **85/85**. Required screenshots captured at 1440×900 (+ viewport checks).

Browser WASM SPA toolchain remains **BLOCKED** (documented below) and is **not** used for the product shell; acceptance for UI 1.1 is against the served `examples/erp/public/` SPA (vanilla JS, not React/Vue).

---

## Architecture

```
examples/erp/public/
  index.html
  css/tokens.css          DesignTokens
  css/app.css             Layout + components
  js/ui.js                Modal, Drawer, Toast, Money/Status helpers
  js/components/index.js  Component builders
  js/data/mock.js         MockDataProvider
  js/data/provider.js     DataProvider + RpcDataProvider stub
  js/registry.js          PageRegistry + menu groups
  js/templates.js         Page templates
  js/app.js               AppShell / tabs / search / notify / user
```

Data path: pages → `JnData.active` → `MockDataProvider` (DEMO).  
`RpcDataProvider` reserved for future server wiring without page rewrite.

---

## Design System

`DesignTokens` in `css/tokens.css`:

| Token | Role |
|-------|------|
| PrimaryColor / BackgroundColor / SurfaceColor / TextColor / BorderColor | Core surfaces |
| SuccessColor / WarningColor / DangerColor | Status |
| Typography | Family + size scale |
| Spacing | 4–32px |
| Radius / Shadow | sm/md/lg |
| ComponentSize | control heights |

All pages share `app.css` — no per-page ad-hoc themes.

---

## Components

### Base

Button, IconButton, TextInput, NumberInput, MoneyInput, DatePicker (native), Select, Checkbox, Tabs (WorkspaceTabs), Modal, Drawer, Dropdown (user menu), Tooltip (`data-tip`), Toast, Badge/Status, Pagination, DataTable, Form/FormField.

### ERP

FilterBar, DocumentStatus, DocumentNumber, partner/item/warehouse fields, DocumentLinesEditor (add/del/recalc), ApprovalTimeline, Document References / Audit-style timeline panels.

### Templates

DashboardTemplate (KPI + trend + todos + recent + quick actions), MasterDataList/Form (modal), DocumentList/Editor, ReportTemplate, BOM Editor, MRP, Settings, Import/Export.

---

## Navigation

Menu groups: Workbench, Master Data, Sales, Purchasing, Inventory, Finance, Manufacturing, CRM, Reports, **Administration**.

AppShell: Sidebar (collapse), Topbar, Workspace, WorkspaceTabs (session restore), Breadcrumb, GlobalSearch, NotificationCenter, UserMenu.

---

## Page Coverage

See `docs/erp-ui-page-matrix.md` — **85 pages**, all `Implemented=YES`, `Interactive=YES`.

Includes charter modules: Dashboards (6), Master Data, Sales, Purchasing, Inventory, Manufacturing, Finance, CRM, Reports, Administration.

---

## Interactions

| Area | Behavior |
|------|----------|
| Lists | Search, status filter, sort, pagination |
| Forms | Input + required validation + Save DEMO / Cancel |
| Documents | Line add/delete, qty/price edit, auto amount/total |
| Save | Updates mock store only; UI states **DEMO DATA** / toast: not formal DB |
| Tabs | Multi-tab switch; scroll position remembered; sessionStorage restore |

---

## Screenshots

Real Chromium (`examples/erp/public/screenshots/`):

| File | Page |
|------|------|
| 01-dashboard.png | Executive Dashboard (KPI/trend/todo/recent/quick) |
| 02-customer-list.png | Customer List |
| 03-customer-editor.png | Customer Editor |
| 04-sales-order-list.png | Sales Order List |
| 05-sales-order-editor.png | Sales Order Editor |
| 06-purchase-order-editor.png | Purchase Order Editor |
| 07-inventory-balance.png | Inventory Balance |
| 08-journal-entry.png | Journal Entries |
| 09-bom-editor.png | BOM Editor |
| 10-mrp-workbench.png | MRP |
| 11-system-settings.png | System Settings |
| 12-dashboard-1920.png | 1920×1080 |
| 13-dashboard-1366.png | 1366×768 |

Smoke: `menu-smoke.json` → `{ "ok": 85, "fail": [], "total": 85 }`

Script: `examples/erp/scripts/ui-screenshots.mjs`

---

## Browser WASM Verification

| Item | Status |
|------|--------|
| Product UI runtime | `public/` vanilla JS SPA |
| `examples/erp/browser/ErpApp.java` | Probe only |
| Full WebUI IR→WASM for ERP SPA | **BLOCKED** (`BrowserWatEmitter` Counter/hello-dom only) |
| Claim WASM screenshots | **No** |

---

## How to run

```bash
DATABASE_URL='sqlite:./build/erp/ui-demo.db' examples/erp/.jnative/build/erp-server
# http://127.0.0.1:18700/  → Enter Demo Workspace
```

---

## Known Issues

| ID | Sev | Note |
|----|-----|------|
| UI-WASM-01 | P1 | WASM SPA toolchain BLOCKED — product uses public shell |
| UI-UTF8-01 | P2 | Static server ASCII-safe assets only |
| UI-RPC-01 | P2 | RpcDataProvider not connected (by design this phase) |
| UI-PICKER-01 | P2 | Partner/Item selectors are text fields, not popup pickers |

---

## Final acceptance

```
JNATIVE_ONE_UI_1_1_PASS
```

**STOP.** No automatic Backend development in this phase.
