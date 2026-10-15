-- Author: 张晓达
-- Purchase / inventory ops: PO header fields, provisional AP, stock min qty, adjust/count docs.

ALTER TABLE purchase_orders ADD COLUMN warehouse_id INTEGER NOT NULL DEFAULT 0;
ALTER TABLE purchase_orders ADD COLUMN order_date INTEGER NOT NULL DEFAULT 0;
ALTER TABLE purchase_orders ADD COLUMN expected_date INTEGER NOT NULL DEFAULT 0;
ALTER TABLE purchase_orders ADD COLUMN notes TEXT NOT NULL DEFAULT '';
ALTER TABLE purchase_orders ADD COLUMN currency TEXT NOT NULL DEFAULT 'CNY';

ALTER TABLE items ADD COLUMN min_qty INTEGER NOT NULL DEFAULT 0;

ALTER TABLE ap_invoices ADD COLUMN is_provisional INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ap_invoices ADD COLUMN notes TEXT NOT NULL DEFAULT '';

CREATE UNIQUE INDEX IF NOT EXISTS ux_ap_source_gr
    ON ap_invoices (company_id, source_gr_id)
    WHERE source_gr_id IS NOT NULL AND source_gr_id > 0;

CREATE TABLE IF NOT EXISTS inventory_adjustments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    doc_no TEXT NOT NULL,
    warehouse_id INTEGER NOT NULL,
    item_id INTEGER NOT NULL,
    quantity_delta INTEGER NOT NULL,
    reason TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    created_by INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    UNIQUE (company_id, doc_no),
    UNIQUE (company_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS inventory_counts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    doc_no TEXT NOT NULL,
    warehouse_id INTEGER NOT NULL,
    status TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    notes TEXT NOT NULL DEFAULT '',
    created_by INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    posted_at INTEGER NOT NULL DEFAULT 0,
    UNIQUE (company_id, doc_no),
    UNIQUE (company_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS inventory_count_lines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    inventory_count_id INTEGER NOT NULL,
    item_id INTEGER NOT NULL,
    system_qty INTEGER NOT NULL,
    counted_qty INTEGER NOT NULL,
    variance_qty INTEGER NOT NULL
);

INSERT OR IGNORE INTO schema_migrations(version, applied_at, checksum)
VALUES ('007_purchase_ops', 0, 'sqlite');
