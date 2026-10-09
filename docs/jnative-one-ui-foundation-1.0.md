# JNative One — UI Foundation 1.0

作者：张晓达

**Date:** 2026-10-08  
**Product:** JNative One  
**Code:** `examples/erp/public/` (product shell) · `examples/erp/browser/ErpApp.java` (WASM probe)  
**Host evidence:** Darwin arm64 · Native `erp-server` · Chromium via Playwright  

**Stamp:** `JNATIVE_ONE_UI_FOUNDATION_PARTIAL`

---

## Verdict

Interactive ERP Web UI with **85 registered pages**, AppShell, design tokens, MockDataProvider, and local draft editing is **RUNNABLE and smoke-tested** in a real browser.

**JNative Browser WASM WebUI SPA is BLOCKED** at the toolchain layer: `BrowserWatEmitter` only emits a specialized Counter WAT or hello-dom stub and does **not** execute `ErpApp` as a full SPA. Screenshots below are of the **public/ vanilla JS shell** (not React/Vue), **not** claimed as WASM acceptance.

---

## Architecture

```
Browser
  └─ examples/erp/public/index.html
       ├─ css/tokens.css + app.css     Design system
       ├─ js/registry.js               PageRegistry (85 pages)
       ├─ js/data/mock.js              MockDataProvider (DEMO DATA)
       ├─ js/data/provider.js          DataProvider + RpcDataProvider stub
       ├─ js/templates.js              Dashboard / List / Document / Report / BOM / MRP / Settings
       ├─ js/ui.js                     Shared components helpers
       └─ js/app.js                    AppShell (sidebar, topbar, tabs, search, user, toasts)

Future:
  RpcDataProvider → JNative Native Server Typed RPC / REST
  (pages do not need rewrite)

WASM (BLOCKED for SPA):
  examples/erp/browser/ErpApp.java → wasm32-browser backend does not lower full IR
```

**Not used:** React, Vue, or other SPA frameworks.

---

## Design System

Tokens in `examples/erp/public/css/tokens.css`:

| Token group | Examples |
|-------------|----------|
| Color | `--jn-color-primary`, sidebar, surface, demo banner |
| Typography | Segoe UI / IBM Plex Sans stack, xs–2xl sizes |
| Spacing | 4–32px scale |
| Radius / Border / Shadow | sm/md/lg |
| Component size | control heights, sidebar/topbar |

Shared components styled in `css/app.css` (Button, Input, Table, Modal, Tabs, Badge, KPI, Timeline, Toast, Empty/Loading/Error).

---

## Page Registry

`JnRegistry` — every sidebar item maps to `pageId`, `module`, `title`, `route`, `template`, `collection` / `dashKind`, `permissions`.

| Metric | Value |
|--------|-------|
| Registered pages | **85** |
| Menu smoke (Playwright) | **85/85 PASS** |
| Matrix | `docs/erp-ui-page-matrix.md` |

Modules: Workbench, Master Data, Sales, Purchasing, Inventory, Finance, Manufacturing, CRM, Reports, System.

---

## Component Library

### Base (implemented in shell CSS + JS helpers)

Button, IconButton, TextInput, NumberInput, MoneyDisplay, Date input, Select, Tabs, Modal, Dropdown (user menu), Tooltip (title attrs), Toast, Badge/Status, Pagination, DataTable, Form/FormField, Card, EmptyState, LoadingState, ErrorState.

Checkbox/Radio/Drawer: partial (pattern ready via modal/filter; not every page uses them).

### ERP-specific

DocumentNumber, DocumentStatus, DocumentLinesEditor (add/del/recalc), DocumentReferencePanel, ApprovalTimeline, MoneyDisplay, QuantityDisplay, FilterBar, selectors as text fields against mock master data (BusinessPartner/Item/Warehouse/Account via typed fields).

---

## Page Coverage

See `docs/erp-ui-page-matrix.md`.

Templates:

| Template | Use |
|----------|-----|
| `dashboard` | 6 workbench dashboards |
| `masterList` | Master data + read-only ledgers |
| `documentList` + editor modal | Sales/Purchasing/Inventory/Finance docs |
| `bomEditor` / `mrp` | Manufacturing tools |
| `report` | Analysis / statements |
| `settings` / `importExport` | System |

---

## Interactions

Verified locally (DEMO mock store):

- Search / filter / sort / pagination on lists  
- New / Open / Save draft on master + documents  
- Document lines: add, delete, qty/price edit, auto amount/total  
- Sidebar collapse, multi-tabs, breadcrumb, global search → customers filter  
- Notifications panel, user menu sign-out  
- All 85 menu entries load without blank/error (`menu-smoke.json`)

---

## Browser WASM Verification

| Check | Result |
|-------|--------|
| `examples/erp/browser/ErpApp.java` exists | YES |
| `jnative.webui` library exists | YES (limited DSL: `div2`, reactive text, buttons) |
| Full IR→WASM for ErpApp SPA | **NO** |
| Evidence | `BrowserWatEmitter.detectWebUiCounter` / else `emitHelloDomMain`; `WebUiIrToWat` not wired into emit path |
| Counter demo specialized WAT | YES (unrelated to ERP SPA) |
| Claim WASM screenshots as acceptance | **Forbidden / not done** |

**WASM status: BLOCKED**

---

## Screenshots

Real Chromium captures (server `http://127.0.0.1:18700`, Mock DEMO):

| File | Subject |
|------|---------|
| `examples/erp/public/screenshots/01-dashboard.png` | Executive Dashboard |
| `02-customer-list.png` | Customer List |
| `03-customer-editor.png` | Customer Editor modal |
| `04-sales-order-list.png` | Sales Order List |
| `05-sales-order-editor.png` | Sales Order Editor |
| `06-purchase-order-editor.png` | Purchase Order Editor |
| `07-inventory-balance.png` | Inventory Balance |
| `08-journal-entry.png` | Journal Entries |
| `09-bom-editor.png` | BOM Editor |
| `10-mrp-workbench.png` | MRP Workbench |
| `11-system-settings.png` | System Settings |
| `12-dashboard-1920.png` | 1920×1080 |
| `13-dashboard-1366.png` | 1366×768 |

Script: `examples/erp/scripts/ui-screenshots.mjs`  
Smoke: `examples/erp/public/screenshots/menu-smoke.json` → `{ ok: 85, fail: [], total: 85 }`

---

## How to run

```bash
java -jar target/jnative.jar build --project examples/erp
DATABASE_URL='sqlite:./build/erp/ui-demo.db' examples/erp/.jnative/build/erp-server
# open http://127.0.0.1:18700/
# Enter Demo Workspace (DEMO DATA — no formal DB writes from UI)
```

---

## Known Issues

| ID | Severity | Issue |
|----|----------|-------|
| UI-WASM-01 | P0 for WASM path | wasm32-browser does not execute full WebUI ERP SPA |
| UI-UTF8-01 | P1 | Native static file server historically corrupts non-ASCII; public UI forced to ASCII |
| UI-RPC-01 | P2 | RpcDataProvider stub only — backend wiring out of scope for this phase |
| UI-COMP-01 | P2 | Checkbox/Radio/Drawer not used on every page; DatePicker is native input |
| UI-SELECT-01 | P2 | Partner/Item selectors are text fields (mock), not popup pickers |

---

## Final status

```
JNATIVE_ONE_UI_FOUNDATION_PARTIAL
```

Reason: complete interactive DEMO UI **PASS** for page coverage/smoke; **Browser WASM SPA BLOCKED**, so foundation cannot stamp PASS under WASM-first acceptance.

**STOP.** Do not auto-develop Backend in this phase.
