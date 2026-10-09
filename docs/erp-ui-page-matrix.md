# JNative One - ERP UI Page Matrix (UI 1.1)

作者：张晓达

Generated: 2026-10-08
Total pages: 85
Menu smoke: 85/85 PASS (Playwright http://127.0.0.1:18700)
Data: MockDataProvider (DEMO DATA)
Browser WASM SPA: BLOCKED / not used for product shell

| Module | Page | Route | Template | Implemented | Interactive | Visual Verified |
|---|---|---|---|---|---|---|
| Workbench | Executive Dashboard | /dashboard | dashboard | YES | YES | YES |
| Workbench | Sales Dashboard | /dashboard/sales | dashboard | YES | YES | SMOKE |
| Workbench | Purchasing Dashboard | /dashboard/purchasing | dashboard | YES | YES | SMOKE |
| Workbench | Inventory Dashboard | /dashboard/inventory | dashboard | YES | YES | SMOKE |
| Workbench | Finance Dashboard | /dashboard/finance | dashboard | YES | YES | SMOKE |
| Workbench | Manufacturing Dashboard | /dashboard/manufacturing | dashboard | YES | YES | SMOKE |
| Master Data | Customers | /master/customers | masterList | YES | YES | YES |
| Master Data | Suppliers | /master/suppliers | masterList | YES | YES | SMOKE |
| Master Data | Items | /master/items | masterList | YES | YES | SMOKE |
| Master Data | Item Categories | /master/categories | masterList | YES | YES | SMOKE |
| Master Data | Warehouses | /master/warehouses | masterList | YES | YES | SMOKE |
| Master Data | Units | /master/units | masterList | YES | YES | SMOKE |
| Master Data | Price Lists | /master/pricelists | masterList | YES | YES | SMOKE |
| Master Data | Currencies | /master/currencies | masterList | YES | YES | SMOKE |
| Master Data | Employees | /master/employees | masterList | YES | YES | SMOKE |
| Master Data | Departments | /master/departments | masterList | YES | YES | SMOKE |
| Sales | Sales Quotations | /sales/quotations | documentList | YES | YES | SMOKE |
| Sales | Sales Orders | /sales/orders | documentList | YES | YES | YES |
| Sales | Deliveries | /sales/deliveries | documentList | YES | YES | SMOKE |
| Sales | Sales Returns | /sales/returns | documentList | YES | YES | SMOKE |
| Sales | AR Invoices | /sales/ar-invoices | documentList | YES | YES | SMOKE |
| Sales | Sales Analysis | /sales/analysis | report | YES | YES | SMOKE |
| Purchasing | Purchase Requests | /purchasing/requests | documentList | YES | YES | SMOKE |
| Purchasing | Purchase Quotations | /purchasing/quotations | documentList | YES | YES | SMOKE |
| Purchasing | Purchase Orders | /purchasing/orders | documentList | YES | YES | YES |
| Purchasing | Goods Receipts | /purchasing/goods-receipts | documentList | YES | YES | SMOKE |
| Purchasing | Purchase Returns | /purchasing/returns | documentList | YES | YES | SMOKE |
| Purchasing | AP Invoices | /purchasing/ap-invoices | documentList | YES | YES | SMOKE |
| Purchasing | Purchasing Analysis | /purchasing/analysis | report | YES | YES | SMOKE |
| Inventory | Inventory Balance | /inventory/balance | masterList | YES | YES | YES |
| Inventory | Inventory Transactions | /inventory/transactions | masterList | YES | YES | SMOKE |
| Inventory | Inventory Transfers | /inventory/transfers | documentList | YES | YES | SMOKE |
| Inventory | Inventory Counting | /inventory/counting | documentList | YES | YES | SMOKE |
| Inventory | Inventory Adjustments | /inventory/adjustments | documentList | YES | YES | SMOKE |
| Inventory | Batch Management | /inventory/batches | masterList | YES | YES | SMOKE |
| Inventory | Serial Numbers | /inventory/serials | masterList | YES | YES | SMOKE |
| Inventory | Inventory Alerts | /inventory/alerts | masterList | YES | YES | SMOKE |
| Inventory | Inventory Valuation | /inventory/valuation | report | YES | YES | SMOKE |
| Finance | Chart of Accounts | /finance/accounts | masterList | YES | YES | SMOKE |
| Finance | Journal Entries | /finance/journals | documentList | YES | YES | YES |
| Finance | General Ledger | /finance/gl | masterList | YES | YES | SMOKE |
| Finance | Trial Balance | /finance/trial-balance | masterList | YES | YES | SMOKE |
| Finance | Accounts Receivable | /finance/ar | documentList | YES | YES | SMOKE |
| Finance | Accounts Payable | /finance/ap | documentList | YES | YES | SMOKE |
| Finance | Incoming Payments | /finance/incoming-payments | documentList | YES | YES | SMOKE |
| Finance | Outgoing Payments | /finance/outgoing-payments | documentList | YES | YES | SMOKE |
| Finance | Bank Accounts | /finance/banks | masterList | YES | YES | SMOKE |
| Finance | Fiscal Periods | /finance/periods | masterList | YES | YES | SMOKE |
| Finance | Balance Sheet | /finance/balance-sheet | report | YES | YES | SMOKE |
| Finance | Income Statement | /finance/income-statement | report | YES | YES | SMOKE |
| Finance | Cash Flow Statement | /finance/cash-flow | report | YES | YES | SMOKE |
| Manufacturing | BOM | /mfg/bom | documentList | YES | YES | SMOKE |
| Manufacturing | BOM Editor | /mfg/bom-editor | bomEditor | YES | YES | YES |
| Manufacturing | Production Orders | /mfg/orders | documentList | YES | YES | SMOKE |
| Manufacturing | Production Issues | /mfg/issues | documentList | YES | YES | SMOKE |
| Manufacturing | Production Returns | /mfg/returns | documentList | YES | YES | SMOKE |
| Manufacturing | Production Receipts | /mfg/receipts | documentList | YES | YES | SMOKE |
| Manufacturing | MRP Workbench | /mfg/mrp | mrp | YES | YES | YES |
| Manufacturing | Production Cost | /mfg/cost | report | YES | YES | SMOKE |
| Manufacturing | Production Progress | /mfg/progress | report | YES | YES | SMOKE |
| CRM | Leads | /crm/leads | masterList | YES | YES | SMOKE |
| CRM | Opportunities | /crm/opportunities | masterList | YES | YES | SMOKE |
| CRM | Activities | /crm/activities | masterList | YES | YES | SMOKE |
| CRM | Customer Visits | /crm/visits | masterList | YES | YES | SMOKE |
| CRM | Sales Pipeline | /crm/pipeline | report | YES | YES | SMOKE |
| CRM | Sales Forecast | /crm/forecast | report | YES | YES | SMOKE |
| Reports | Sales Reports | /reports/sales | report | YES | YES | SMOKE |
| Reports | Purchasing Reports | /reports/purchasing | report | YES | YES | SMOKE |
| Reports | Inventory Reports | /reports/inventory | report | YES | YES | SMOKE |
| Reports | Finance Reports | /reports/finance | report | YES | YES | SMOKE |
| Reports | Manufacturing Reports | /reports/manufacturing | report | YES | YES | SMOKE |
| Reports | Customer Analytics | /reports/customer | report | YES | YES | SMOKE |
| Reports | Executive Analytics | /reports/executive | report | YES | YES | SMOKE |
| System | Users | /system/users | masterList | YES | YES | SMOKE |
| System | Roles | /system/roles | masterList | YES | YES | SMOKE |
| System | Permissions | /system/permissions | masterList | YES | YES | SMOKE |
| System | Companies | /system/companies | masterList | YES | YES | SMOKE |
| System | Departments | /system/departments | masterList | YES | YES | SMOKE |
| System | Workflows | /system/workflows | masterList | YES | YES | SMOKE |
| System | Custom Fields | /system/custom-fields | masterList | YES | YES | SMOKE |
| System | Form Configuration | /system/forms | masterList | YES | YES | SMOKE |
| System | Numbering Series | /system/numbering | masterList | YES | YES | SMOKE |
| System | System Settings | /system/settings | settings | YES | YES | YES |
| System | Audit Logs | /system/audit | masterList | YES | YES | SMOKE |
| System | Import Export | /system/import-export | importExport | YES | YES | SMOKE |
