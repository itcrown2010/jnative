# Document Editor components (UI 1.2)

**Runtime implementation:** `examples/erp/public/js/components/document/`

| Architectural name | Public SPA module |
|---|---|
| DocumentEditor | `editor.js` → `JnDocumentEditor` |
| DocumentHeader | rendered by `editor.js` `headerHtml()` |
| DocumentLinesEditor | rendered by `editor.js` `linesHtml()` |
| DocumentFooter | rendered by `editor.js` `footerHtml()` |
| DocumentToolbar | rendered by `editor.js` `toolbarHtml()` |
| DocumentStatus | `JnUI.statusHtml` + editor status field |
| DocumentReferencePanel | `panelsHtml()` references card |
| DocumentAttachmentPanel | `panelsHtml()` attachments card |
| DocumentAuditPanel | `panelsHtml()` audit + ApprovalTimeline |
| DocumentUiDefinition | `definition.js` → `JnDocumentDefs` |
| DocumentCalculationService | `calc.js` → `JnDocumentCalc` |
| ItemSelector / BusinessPartnerSelector | `selectors.js` → `JnSelectors` |

JNative Browser WASM SPA remains **BLOCKED** (see `ErpApp.java`). These stubs document the intended component map for a future WASM port; acceptance for UI 1.2 is the served `public/` SPA.
