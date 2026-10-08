# JNative One — UI 1.2 Enterprise Document Editor

**Date:** 2026-10-08  
**Principles:** UI FIRST · Mock Data allowed · No new ERP backend business logic  
**Scope:** Reusable document editor for six ERP document types  

**Stamp:** `JNATIVE_ONE_UI_1_2_PASS`

---

## Verdict

Six document types open in a real Chromium browser against the served `examples/erp/public/` SPA. Editor interactions (partner/item selectors, line CRUD, copy, TSV paste with reject-on-error, live integer-cent calc, Save Draft to MockDataProvider + sessionStorage, unsaved-changes confirm) verified by Playwright (`examples/erp/scripts/ui-1.2-verify.mjs`). Result artifact: `examples/erp/public/screenshots/ui12-verify.json` (`ok: 21`, `fail: []`).

JNative Browser WASM SPA remains **BLOCKED** (same as UI 1.0/1.1). Product acceptance for UI 1.2 is the vanilla JS public SPA, not WASM.

---

## Architecture

```
examples/erp/public/js/components/document/
  definition.js   DocumentUiDefinition (strong typed, no eval)
  calc.js         DocumentCalculationService (integer cents)
  selectors.js    Item / Partner / Warehouse / Employee pickers
  editor.js       DocumentEditor + Header/Lines/Footer/Toolbar/Panels

examples/erp/browser/components/document/
  *.java + README.md   architectural stubs mapping to public/ runtime
```

Wiring:

- `index.html` loads the four document scripts before `registry.js` / `templates.js`
- `templates.js` `openEditor` routes pages with `JnDocumentDefs.getByPageId` → `JnDocumentEditor.openByPage`
- Registry `editor: true` on Sales Orders, Deliveries, AR; Purchase Orders, Goods Receipts, AP

Reuse from UI 1.0/1.1: AppShell, DataTable list pages, Form grid, Modal backdrop, Select/DatePicker patterns, Money display helpers, Toast, Status badge, DesignTokens.

---

## Document UI Model

`DocumentUiDefinition` fields:

| Field | Role |
|-------|------|
| documentType | SalesOrder / PurchaseOrder / Delivery / GoodsReceipt / ARInvoice / APInvoice |
| title | Editor title |
| headerFields | typed field descriptors (text/date/partner/currency/warehouse/employee/contact) |
| lineColumns | typed columns (item/qty/money/percent/…) |
| footerFields | totals display |
| availableActions | save/submit/approve/reject/copy/cancel/close |
| validationRules | `{ scope, field, rule }` — no arbitrary code |
| taxMode | `exclusive` (demo default) \| `inclusive` |
| rounding | `half-up-cent` (demo) |

---

## Component Reuse

| UI 1.2 name | Implementation |
|-------------|----------------|
| DocumentEditor | `JnDocumentEditor.open` |
| DocumentHeader / Lines / Footer / Toolbar / Status | functions inside `editor.js` |
| DocumentReferencePanel / AttachmentPanel / AuditPanel + ApprovalTimeline | `panelsHtml()` |
| ItemSelector / BusinessPartnerSelector | `JnSelectors` |
| DocumentCalculationService | `JnDocumentCalc` |

---

## Editor Modes

| Mode | Behavior |
|------|----------|
| CREATE | Editable; default DRAFT |
| EDIT | Editable only when status is DRAFT or SUBMITTED; otherwise remapped to VIEW |
| VIEW | Read-only; Copy/Close only |

Statuses (DEMO UI only — not formal posting): DRAFT, SUBMITTED, APPROVED, CANCELLED (plus legacy list statuses OPEN/POSTED/PARTIAL open as VIEW).

---

## Keyboard Interaction

Line cells (IME-safe: ignore when `isComposing` / keyCode 229):

- Tab / Shift+Tab — next/prev editable cell; Tab on last cell adds a line
- Enter / ArrowDown / ArrowUp — move rows
- ArrowLeft/Right — at caret edges, move cells
- Escape — blur cell
- Ctrl+Delete on empty cell — delete row

Selectors: search Enter, ArrowDown into grid, ArrowUp/Down rows, Enter confirm, Escape cancel.

---

## Calculation Rules (DEMO)

Source of truth: **integer minor units (cents)**. No binary float as money authority.

```
gross      = qty * unitPriceCents
discount   = half-up(gross * discountBps / 10000)
net        = gross - discount
tax (excl) = half-up(net * taxBps / 10000)
lineAmount = net + tax
document totals = sum(net), sum(tax), sum(lineAmount)
```

Verified fixture: qty=10, unit=8000¢, disc=10%, tax=13% → discount=8000, tax=9360, line=81360.

Legacy mock `price`/`amount`/`total` yuan fields migrate to cents on open; list display still uses `total` yuan for compatibility.

---

## Validation

- Header required fields (partner, date, …)
- At least one line
- Line item required; qty must be > 0
- FieldError / RowError via `.jn-cell-err` + `.err-msg`; DocumentError via Toast
- TSV paste: column map sku/name/qty/price/disc%/tax%; **rejects** on first error (no silent bad rows); confirm before apply

---

## Save Draft / Unsaved

- **Save Draft** → `MockDataProvider` in-memory + `sessionStorage` key `jn_doc_drafts_v12`
- Toast explicitly says DEMO / not formal DB
- **sessionStorage drafts survive refresh of the same tab; cleared when the browser tab/session ends**
- Unsaved: Close confirm; AppShell `navigate` while dirty also confirms (sidebar clicks blocked by modal — navigate API covered)

Attachments: file name metadata only; never claimed as server upload.

---

## Browser Verification

| Item | Result |
|------|--------|
| Runtime | Chromium via Playwright |
| Base URL | `http://127.0.0.1:18700/` (erp-server static `public/`) |
| Script | `examples/erp/scripts/ui-1.2-verify.mjs` |
| Six editors | PASS |
| Selectors / lines / paste / save / unsaved | PASS |
| Calc unit check | PASS |
| WASM SPA | BLOCKED (not used for acceptance) |

Covered interactions: new doc, select customer, select item, add/copy/delete line, change qty/price, paste TSV, save draft, cancel/close, unsaved navigate dismiss.

---

## Performance (measured, not invented)

From `ui12-verify.json` on this machine (Chromium, wall-clock `performance.now`):

| Lines | renderMs | calcMs | inputMs | scrollMs | wallMs |
|------:|---------:|-------:|--------:|---------:|-------:|
| 10 | 1 | 0 | 0 | 2 | 3 |
| 100 | 3 | 0 | 0 | 16 | 21 |
| 500 | 16 | 0 | 2 | 90 | 116 |

Notes: `calcMs` often rounds to 0 for these sizes; 500-line wall ~116ms dominated by DOM paint + scroll probe. Not a formal SLA.

---

## Screenshots

All under `examples/erp/public/screenshots/` (1440×900):

| File | Subject |
|------|---------|
| `ui12-sales-order-editor.png` | Sales Order Editor |
| `ui12-purchase-order-editor.png` | Purchase Order Editor |
| `ui12-delivery-editor.png` | Delivery Editor |
| `ui12-goods-receipt-editor.png` | Goods Receipt Editor |
| `ui12-ar-invoice-editor.png` | AR Invoice Editor |
| `ui12-ap-invoice-editor.png` | AP Invoice Editor |
| `ui12-item-selector.png` | Item Selector |
| `ui12-business-partner-selector.png` | Business Partner Selector |

---

## Known Issues

1. **WASM SPA BLOCKED** — `browser/components/document/*` are architectural stubs; runtime is `public/`.
2. Document editor is a full-viewport modal; sidebar click-through for unsaved navigate is blocked by backdrop (AppShell `JnApp.navigate` still guards dirty state).
3. List collections still store legacy yuan `total`/`price` for older templates; editor migrates on open.
4. Arrow-key cell navigation skips `item` picker cells (open via `...` button).
5. Dense 500-line editors use full DOM rows (no virtualization yet) — acceptable for UI 1.2 demo scale.

---

## Acceptance Checklist

| Criterion | Status |
|-----------|--------|
| Six document pages runnable | PASS |
| Edit interactions | PASS |
| Amount calculation (cents) | PASS |
| Selectors | PASS |
| Keyboard ops (IME-safe) | PASS |
| Real browser verification | PASS |
| Honest draft/save messaging | PASS |

**Stamp: `JNATIVE_ONE_UI_1_2_PASS`**

STOP — do not auto-start the next phase.
