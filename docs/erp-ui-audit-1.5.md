# ERP UI Audit — UI 1.5 Preflight

作者：张晓达

**Date:** 2026-10-08  
**Sources inspected:** `examples/erp/public/`, docs `jnative-one-ui-1.1`…`1.4`, browser WASM stubs  
**Assumption:** Prior Cursor phases are **not** trusted until verified in-tree.

---

## Verified prior stamps (docs present)

| Phase | Doc | Claimed stamp | Runtime surface |
|-------|-----|---------------|-----------------|
| UI 1.1 | `docs/jnative-one-ui-1.1.md` | PASS | `public/` SPA |
| UI 1.2 | `docs/jnative-one-ui-1.2.md` | PASS | Document editor |
| UI 1.3 | `docs/jnative-one-ui-1.3.md` | PASS | EnterpriseDataGrid |
| UI 1.4 | `docs/jnative-one-ui-1.4.md` | PASS | DemoStateStore + ApplicationService |

WASM SPA (`browser/ErpApp.java`): **BLOCKED** (Counter/hello-dom only) — product UI is vanilla `public/`.

---

## Pages

- **85** routes in `JnRegistry` (`ui14-page-coverage.json`: 85 loaded).
- Modules: Workbench, Master Data, Sales, Purchasing, Inventory, Finance, Manufacturing, CRM, Reports, Administration.
- Status mix (1.4): INTERACTIVE ~68, PREVIEW ~17 (mostly reports).

---

## Components (present)

| Area | Location |
|------|----------|
| DesignTokens | `css/tokens.css` |
| AppShell / tabs / search | `js/app.js` |
| UI helpers | `js/ui.js`, `js/components/index.js` |
| Document editor | `js/components/document/*` |
| Grid / filter / views | `js/components/grid/*`, `query/*` |
| Demo engine | `js/demo/{events,seed,store,services}.js` |
| Nav / global search | `js/components/nav/*` |

---

## Visual issues (pre-1.5)

1. Density is single-size; tables feel tall on 1366 screens.
2. Dashboard KPI cards / charts share markup but spacing varies vs lists.
3. Document editor vs list chrome: slightly different toolbar density.
4. Report pages are thin list wrappers — weak “report product” framing.
5. Settings is a flat key/value grid — no category nav.
6. No first-class Master Data **detail** template (edit = modal only).
7. Focus rings / contrast on sidebar muted text could be stronger.
8. Theme is light-only with no reserved dark token set.

---

## Interaction issues (pre-1.5)

1. No Ctrl/Cmd+S save binding in document editor.
2. No product Demo Center / guided tour.
3. Page switch remounts full template — minor flash possible.
4. Empty/error/loading patterns exist but not fully standardized across reports.
5. Confirmation dialogs mix `window.confirm` and modal — inconsistent.

---

## Unimplemented / thin (pre-1.5)

- DemoCenter / DemoTourEngine / scenario runners
- Comfortable / Compact density
- Dark theme (reserve only)
- VisualRegressionSuite automation
- MasterDataDetailTemplate / ReportPageTemplate / SettingsPageTemplate polish layers
- Commercial accessibility pass (focus, labels, contrast checklist)

---

## 1.5 scope decision

Polish **in place** on `public/` SPA; keep DemoStateStore; no new ERP backend modules; WASM remains BLOCKED for acceptance.
