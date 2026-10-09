# JNative One — UI 1.6 Full UI Acceptance & UX Hardening

作者：张晓达

**Date:** 2026-10-08  
**Principles:** UI FIRST · No new ERP business modules · Honest measurement · DemoStateStore only  
**Stamp:** `JNATIVE_ONE_UI_1_6_PASS`

---

## Verdict

Full UI acceptance on `examples/erp/public/` SPA: **86/86** pages load, five demo business chains pass, core interactions / selectors / document editors / grid / keyboard / a11y / viewports verified in real Chromium. **0 P0 / 0 P1** open. Playwright: **43 OK / 0 FAIL**.

WASM Browser ERP UI remains **BLOCKED** (probe only). Acceptance surface is the public SPA.

Artifacts:

| Artifact | Path |
|----------|------|
| Acceptance matrix | `docs/erp-ui-acceptance-matrix.md` |
| Verify JSON | `examples/erp/public/screenshots/ui16-verify.json` |
| Issues JSON | `examples/erp/public/screenshots/ui16-issues.json` |
| Suite | `examples/erp/scripts/ui-1.6-verify.mjs` |

---

## Docs Audit

| File | Status |
|------|--------|
| `docs/jnative-one-ui-1.1.md` | OK |
| `docs/jnative-one-ui-1.2.md` | OK |
| `docs/jnative-one-ui-1.3.md` | OK |
| `docs/jnative-one-ui-1.4.md` | OK |
| `docs/jnative-one-ui-1.5.md` | OK |

Prior stamps were verified as present in-tree; runtime re-proven by this suite (not assumed).

---

## Page Coverage

| Metric | Value |
|--------|------:|
| Registered pages | 86 |
| LoadTest PASS | 86 |
| Blank / error pages | 0 |
| ButtonActionRegistry entries | 18 |

Full matrix: `docs/erp-ui-acceptance-matrix.md`  
Fields: Module · Page · Route · Implemented · LoadTest · InteractionTest · VisualTest · WorkflowTest · Status.

---

## Interaction Coverage

| Area | Result |
|------|--------|
| Sidebar / Topbar / Tabs / Breadcrumb / hash navigate | PASS |
| ButtonActionRegistry probe (density / filter / columns) | PASS |
| Forms (fields, validation path, cancel/close) | PASS |
| Document editors SO / PO / Delivery / GR / AR / AP | PASS |
| EnterpriseDataGrid search / sort / pagination / batch | PASS |
| Customer / Supplier / Item / Warehouse / **Account** selectors | PASS |
| Modal open / focus / Escape | PASS |
| Keyboard Tab / Shift+Tab / Ctrl+S / Escape | PASS |
| IME | No composition-breaking handlers (keyCode 229 / isComposing guards kept) |

---

## Workflow Results

All via **ApplicationService → DemoStateStore** (no formal DB writes).

| Chain | Result | Key check |
|-------|--------|-----------|
| Sales | PASS | Create customer → SO → delivery → AR → payment; stock **70** |
| Purchasing | PASS | Supplier → PO → GR → AP → payment; stock after GR **100** |
| Inventory | PASS | Transfer WH-MAIN→WH-FG2 + adjust; balances match expected |
| Finance | PASS | Margin **900**; all journals **balanced** |
| Manufacturing | PASS | MO → issue RM → receipt FG; status **CLOSED** |
| Workbench KPIs | PASS | Same object as `JnDemoState.computeKpis()` |

---

## Visual Consistency

DesignTokens (`css/tokens.css` / JNativeOneTheme) drive colors, spacing, density, status tags, money/qty cells. Light theme default; Compact/Comfortable density retained from UI 1.5.

---

## Browser Verification

| Field | Value |
|-------|--------|
| Browser | Chromium (Playwright) |
| OS | darwin (host) |
| Build | `examples/erp/public` SPA |
| Runtime / console errors (final run) | none |
| WASM SPA | **BLOCKED** — `browser/ErpApp.java` probe only |

---

## Performance

Measured (not fabricated), Chromium, synthetic `perfCustomers` via `JnGridData.active.ensureSynthetic`:

| Rows | genMs | queryMs | filterMs | sortMs | heapMb |
|-----:|------:|--------:|---------:|-------:|-------:|
| 100 | 0 | 0 | 0 | 0 | 10 |
| 1,000 | 0 | 1 | 0 | 1 | 10 |
| 10,000 | 2 | 3 | 2 | 3 | 10 |
| 100,000 | — | **29** | **10** | **28** | 10 |

| Probe | ms |
|-------|---:|
| First load (+ reset seed) | 1057 |
| Page switch (customers↔orders) | 34 |
| Scroll probe | ~0 (sync) |

100k query remains well under 5s on this host (paged query over in-memory mock).

---

## Accessibility

| Check | Result |
|-------|--------|
| DEMO MODE `role="status"` | Present |
| Focusable controls (sample) | 201 |
| Labels (sample page) | 5+ |
| Body contrast sample | dark text on light surface |
| Modal Escape + initial focus | Hardened in `JnUI.modal` |

---

## Hardening Fixes (this phase)

1. **AccountSelector** added (`JnSelectors.openAccountSelector` + Customer/Supplier aliases).  
2. **Manufacturing + inventory ops** golden helpers on `JnAppService`.  
3. **ButtonActionRegistry / UIIssue** modules under `public/js/acceptance/`.  
4. Safe `Element.closest` guards (tour / editor / grid / selectors) — fixes `closest is not a function` on non-Element targets.  
5. Document editor leaked keydown listeners self-clean when backdrop removed; `dismissAll()`.  
6. Modal Escape + focus management.

No formal ERP backend modules added.

---

## Known Issues

| ID | Severity | Status | Notes |
|----|----------|--------|-------|
| WASM SPA | — | BLOCKED | Not acceptance surface; documented |
| Dark theme | P2 | Deferred | Reserved tokens; Light only (UI 1.5) |
| Report pages | — | PREVIEW | Load OK; thin analytics framing |
| Open P0 / P1 | — | **None** | `ui16-issues.json` empty |

---

## Screenshots

Under `examples/erp/public/screenshots/`:

- ui16-dashboard.png  
- ui16-customer-list.png / ui16-customer-detail.png  
- ui16-sales-order-editor.png / ui16-purchase-order-editor.png  
- ui16-inventory-balance.png / ui16-journal-entry.png  
- ui16-bom-editor.png / ui16-mrp-workbench.png  
- ui16-demo-center.png / ui16-settings.png  
- ui16-viewport-1366.png / 1440 / 1920  

---

## Acceptance Gate

| Criterion | Result |
|-----------|--------|
| All planned pages accessible | PASS (86/86) |
| Core interactions pass | PASS |
| Five demo business chains | PASS |
| No open P0 | PASS |
| Key P1 resolved | PASS |
| Real browser verification | PASS |

```
JNATIVE_ONE_UI_1_6_PASS
```

**STOP** — no Backend development in this phase.
