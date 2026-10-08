# JNative One — UI 1.5 Commercial UI Polish & Demo Center

**Date:** 2026-10-08  
**Principles:** UI FIRST · EnterpriseDesignSystem · DemoStateStore only · No new formal ERP backend modules  
**Stamp:** `JNATIVE_ONE_UI_1_5_PASS`

---

## Verdict

Commercial polish landed on the existing `examples/erp/public/` SPA: unified design tokens (Light + density), shell/layout consistency, dashboard/grid/form/document templates, Demo Center + Tour Engine (6 scenarios), visual regression screenshots, viewport + a11y + measured perf probes.

Playwright Chromium: **26 OK / 0 FAIL**. Artifact: `examples/erp/public/screenshots/ui15-verify.json`.

WASM SPA (`browser/ErpApp.java`) remains **BLOCKED** for product acceptance (same as UI 1.1–1.4).

No formal ERP backend modules were added in this phase.

---

## UI Audit

Preflight audit (no assumed completion of prior phases): `docs/erp-ui-audit-1.5.md`.

| Finding | Pre-1.5 | 1.5 result |
|---------|---------|------------|
| Density | Single size | Comfortable / Compact |
| Dark theme | None | Reserved tokens; Light shipped |
| Demo Center / Tour | Missing | 6 scenarios + tour chrome |
| Master detail / Report / Settings templates | Thin | Dedicated templates |
| Ctrl/Cmd+S | Missing | Bound on DocumentEditor |
| Visual regression automation | Ad-hoc | `scripts/ui-1.5-verify.mjs` |
| DEMO MODE isolation | Banner + DemoStateStore | Unchanged; still no formal DB writes |

---

## Design System

**EnterpriseDesignSystem** — `examples/erp/public/css/tokens.css`  
**JNativeOneTheme** — `examples/erp/public/js/theme.js`

| Token group | Notes |
|-------------|--------|
| Colors | Surface / text / border / accent / status (open/draft/error/ok) |
| Typography | Scale via `--jn-font-*`; body contrast sample `rgb(26, 39, 51)` |
| Spacing | `--jn-space-*` |
| Radius / Shadow / Borders | Shared chrome for cards, panels, modals |
| ComponentSize | Button / input / toolbar heights tied to density |
| Density | `data-density=comfortable\|compact` → `--jn-row-h` **36px / 28px** |

Light Theme is default and verified. Dark Theme CSS variables are reserved under `[data-theme="dark"]` but not product-enabled this phase.

Density applies to DataTable, Form, DocumentEditor, Toolbar, and list chrome via shared CSS variables.

---

## Visual Consistency

| Surface | Polish |
|---------|--------|
| AppShell / Sidebar / Topbar | Shared tokens; DEMO MODE banner `role="status"` |
| WorkspaceTabs / Breadcrumb | Stable chrome; page switch fade ~0.12s |
| Dashboards (Exec / Sales / Pur / Inv / Fin / Mfg) | Shared KPI card / chart / task / recent / quick-action patterns |
| EnterpriseDataGrid | Header, row height, status tags, money/qty formatting via density |
| Form / FormField / Select / Date / Money / Number / Validation | Shared `.jn-input` / field chrome |
| DocumentHeader / Lines / Footer / Toolbar | Fixed toolbar; amount region; denser lines under Compact |
| MasterDataDetailTemplate | Basic / related / contacts / attachments / audit tabs |
| ReportPageTemplate | Title, filters, KPIs, grid, CSV export note |
| SettingsPageTemplate | Category nav, panel, save / reset, density control |

---

## Interaction Quality

| Item | Status |
|------|--------|
| Loading / Success / Warning / Error / Empty | Shared `JnUI` toast + empty / status classes |
| Confirmation / Unsaved dialogs | `JnUI.confirmDialog` (modal; not `window.confirm` for product flows) |
| Tab / Shift+Tab / Enter / Escape | Shell + modal focusable set (a11y probe: **181** focusables) |
| Ctrl/Cmd+S | Document editor save path |
| Arrow keys | Grid / tour navigation (existing) |
| IME | No composition-breaking key handlers added |

---

## Demo Center

| Piece | Location |
|-------|----------|
| DemoCenter UI | `js/demo/center.js` + registry `demo.center` |
| DemoTourEngine | `js/demo/tour.js` (spotlight, next/prev/skip/finish) |
| Scenarios | Executive, Sales, Purchasing, Inventory, Finance, Manufacturing |
| State | All mutations remain via **DemoStateStore / ApplicationService** |
| Safety | Always **DEMO MODE**; no formal business DB writes |

Verified starts (tour card titles): Executive Dashboard, Sales Orders, Purchase Orders, Inventory Balance, Journal Entries, BOM Editor.

Controls: start / pause / resume / exit / reset (Demo Center + tour chrome).

---

## Visual Regression

Suite: `examples/erp/scripts/ui-1.5-verify.mjs` → `VisualRegressionSuite` via Chromium screenshots (bytes > 8KB gate).

| Shot | Bytes | OK |
|------|------:|:--:|
| ui15-executive-dashboard.png | 163747 | ✓ |
| ui15-customer-list.png | 191618 | ✓ |
| ui15-customer-detail.png | 118308 | ✓ |
| ui15-sales-order-editor.png | 155958 | ✓ |
| ui15-purchase-order-editor.png | 161950 | ✓ |
| ui15-inventory-balance.png | 196293 | ✓ |
| ui15-journal-entry.png | 114270 | ✓ |
| ui15-bom-editor.png | 112049 | ✓ |
| ui15-demo-center.png | 152265 | ✓ |
| ui15-settings.png | 116349 | ✓ |
| ui15-viewport-1366.png | 194942 | ✓ |
| ui15-viewport-1440.png | 208514 | ✓ |
| ui15-viewport-1920.png | 217413 | ✓ |

---

## Browser Verification

| Check | Result |
|-------|--------|
| Engine | Playwright Chromium |
| Login shell | OK |
| Density tokens | compact=28px / comfortable=36px |
| Light theme | OK |
| DEMO MODE banner | OK |
| 6 demo scenarios | OK |
| Ctrl+S on SO editor | OK |
| Viewports 1366×768 / 1440×900 / 1920×1080 | OK |
| WASM product UI | **BLOCKED** (acceptance = `public/` SPA) |

Serve note for this run: static `python3 -m http.server` on `examples/erp/public` (SPA assets). Formal `erp-server` is not required for UI 1.5 acceptance.

---

## Performance

Measured (Chromium, 1440×900):

| Probe | ms |
|-------|---:|
| First load (`networkidle`) | 529 |
| Nav Executive | 314 |
| Nav Customers | 318 |
| Nav Sales Orders | 304 |
| Nav Purchase Orders | 320 |
| Nav Inventory | 321 |
| Nav Journals | 316 |
| Nav BOM Editor | 304 |
| Nav Demo Center | 303 |
| Nav Settings | 303 |
| In-app switch (`JnApp.navigate`) | 53 |
| Grid scroll probe | 0 (sync) |
| Filter builder open | 0 (sync) |
| Document editor open | 0 (sync) |

Page switches remount templates; fade-in reduces perceived flash. No formal backend latency in this path.

---

## Screenshots

Real browser captures under `examples/erp/public/screenshots/`:

- Executive Dashboard, Customer List, Customer Detail  
- Sales Order Editor, Purchase Order Editor  
- Inventory Balance, Journal Entry, BOM Editor  
- Demo Center, Settings  
- Viewport set: 1366 / 1440 / 1920  

---

## Accessibility

| Check | Result |
|-------|--------|
| DEMO MODE `role="status"` | Present |
| Unlabeled `.jn-input` sample (settings context) | 0 |
| Focusable controls | 181 |
| Body text color | `rgb(26, 39, 51)` on light surface |
| Error / validation messaging | Shared field + toast patterns |

Full WCAG audit is out of scope; spot checks passed for this gate.

---

## Product Demo Acceptance

| Flow | Nav | Tour start | Notes |
|------|-----|------------|-------|
| Sales | demo → sales | Sales Orders | DemoStateStore |
| Purchasing | demo → purchasing | Purchase Orders | DemoStateStore |
| Inventory | demo → inventory | Inventory Balance | DemoStateStore |
| Finance | demo → finance | Journal Entries | DemoStateStore |
| Manufacturing | demo → manufacturing | BOM Editor | DemoStateStore |
| Executive | demo → executive | Executive Dashboard | KPI cards |

---

## Known Issues

1. **WASM Browser ERP UI** still BLOCKED — Counter/hello-dom only; product shell is `public/`.
2. **Dark Theme** reserved only; not user-selectable in Settings this phase.
3. Some report pages remain read-oriented **PREVIEW** slices (UI 1.4 coverage); ReportPageTemplate frames them but does not deepen every report.
4. Sync perf probes (filter/doc open) report ~0ms — useful as smoke, not as load-test SLOs.
5. `erp-server` static serving of non-ASCII was historically fragile; UI 1.5 assets are ASCII-sanitized where needed; acceptance used Python static server.

---

## Deliverables

| Artifact | Path |
|----------|------|
| Audit | `docs/erp-ui-audit-1.5.md` |
| This report | `docs/jnative-one-ui-1.5.md` |
| Tokens / theme | `public/css/tokens.css`, `public/js/theme.js` |
| Demo Center / Tour | `public/js/demo/center.js`, `tour.js` |
| Verify script | `examples/erp/scripts/ui-1.5-verify.mjs` |
| Results JSON | `public/screenshots/ui15-verify.json` |

---

## Stamp

```
JNATIVE_ONE_UI_1_5_PASS
```

Criteria met: primary pages visually consistent · primary interactions OK · demo flows complete · visual regression passed · real browser verification passed.

**STOP** — no Backend development in this phase.
