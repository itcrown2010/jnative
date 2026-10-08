-- Pilot baseline (SQLite)
-- Note: jnative SQLite prepare rejects multiple table-level UNIQUE constraints;
-- use separate CREATE UNIQUE INDEX instead.

CREATE TABLE IF NOT EXISTS demo_seed_meta (
    company_id INTEGER NOT NULL PRIMARY KEY,
    seed_version INTEGER NOT NULL,
    applied_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS setup_wizard_state (
    company_id INTEGER NOT NULL PRIMARY KEY,
    step_code TEXT NOT NULL DEFAULT 'COMPANY',
    payload_json TEXT NOT NULL DEFAULT '{}',
    completed INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL,
    updated_by INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS opening_stock_docs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    doc_no TEXT NOT NULL,
    warehouse_id INTEGER NOT NULL,
    business_date INTEGER NOT NULL,
    status TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    notes TEXT NOT NULL DEFAULT '',
    created_by INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    posted_at INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_opening_stock_docs_doc_no ON opening_stock_docs (company_id, doc_no);
CREATE UNIQUE INDEX IF NOT EXISTS ux_opening_stock_docs_idem ON opening_stock_docs (company_id, idempotency_key);

CREATE TABLE IF NOT EXISTS opening_stock_lines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    doc_id INTEGER NOT NULL,
    item_id INTEGER NOT NULL,
    quantity INTEGER NOT NULL,
    unit_cost_amount INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS opening_ar_docs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    doc_no TEXT NOT NULL,
    customer_id INTEGER NOT NULL,
    business_date INTEGER NOT NULL,
    amount INTEGER NOT NULL,
    status TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    notes TEXT NOT NULL DEFAULT '',
    created_by INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    posted_at INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_opening_ar_docs_doc_no ON opening_ar_docs (company_id, doc_no);
CREATE UNIQUE INDEX IF NOT EXISTS ux_opening_ar_docs_idem ON opening_ar_docs (company_id, idempotency_key);

CREATE TABLE IF NOT EXISTS opening_ap_docs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    doc_no TEXT NOT NULL,
    supplier_id INTEGER NOT NULL,
    business_date INTEGER NOT NULL,
    amount INTEGER NOT NULL,
    status TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    notes TEXT NOT NULL DEFAULT '',
    created_by INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    posted_at INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_opening_ap_docs_doc_no ON opening_ap_docs (company_id, doc_no);
CREATE UNIQUE INDEX IF NOT EXISTS ux_opening_ap_docs_idem ON opening_ap_docs (company_id, idempotency_key);

CREATE TABLE IF NOT EXISTS import_batches (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    batch_code TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    status TEXT NOT NULL,
    total_rows INTEGER NOT NULL DEFAULT 0,
    ok_rows INTEGER NOT NULL DEFAULT 0,
    error_rows INTEGER NOT NULL DEFAULT 0,
    error_report TEXT NOT NULL DEFAULT '',
    created_by INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_import_batches_code ON import_batches (company_id, batch_code);
