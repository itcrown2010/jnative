-- Author: 张晓达
-- Sales ops: approval/fulfillment split, reservations, returns, provisional AR columns.

ALTER TABLE sales_orders ADD COLUMN warehouse_id INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sales_orders ADD COLUMN order_date INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sales_orders ADD COLUMN delivery_date INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sales_orders ADD COLUMN notes TEXT NOT NULL DEFAULT '';
ALTER TABLE sales_orders ADD COLUMN currency TEXT NOT NULL DEFAULT 'CNY';
ALTER TABLE sales_orders ADD COLUMN approval_status TEXT NOT NULL DEFAULT 'DRAFT';
ALTER TABLE sales_orders ADD COLUMN fulfillment_status TEXT NOT NULL DEFAULT 'NONE';

ALTER TABLE sales_order_lines ADD COLUMN returned_qty INTEGER NOT NULL DEFAULT 0;

ALTER TABLE ar_invoices ADD COLUMN is_provisional INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ar_invoices ADD COLUMN notes TEXT NOT NULL DEFAULT '';

CREATE UNIQUE INDEX IF NOT EXISTS ux_ar_source_dl
    ON ar_invoices (company_id, source_delivery_id)
    WHERE source_delivery_id IS NOT NULL AND source_delivery_id > 0;

CREATE TABLE IF NOT EXISTS inventory_reservations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    sales_order_id INTEGER NOT NULL,
    sales_order_line_id INTEGER NOT NULL,
    warehouse_id INTEGER NOT NULL,
    item_id INTEGER NOT NULL,
    quantity INTEGER NOT NULL,
    quantity_consumed INTEGER NOT NULL DEFAULT 0,
    quantity_released INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_inv_res_wh_item
    ON inventory_reservations (company_id, warehouse_id, item_id, status);

CREATE INDEX IF NOT EXISTS idx_inv_res_so_line
    ON inventory_reservations (company_id, sales_order_line_id, status);

CREATE TABLE IF NOT EXISTS sales_returns (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    doc_no TEXT NOT NULL,
    sales_order_id INTEGER NOT NULL,
    delivery_id INTEGER NOT NULL,
    warehouse_id INTEGER NOT NULL,
    status TEXT NOT NULL,
    resellable INTEGER NOT NULL DEFAULT 1,
    idempotency_key TEXT NOT NULL,
    notes TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    UNIQUE (company_id, doc_no),
    UNIQUE (company_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS sales_return_lines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    sales_return_id INTEGER NOT NULL,
    delivery_line_id INTEGER NOT NULL,
    sales_order_line_id INTEGER NOT NULL,
    item_id INTEGER NOT NULL,
    quantity INTEGER NOT NULL
);

ALTER TABLE delivery_lines ADD COLUMN returned_qty INTEGER NOT NULL DEFAULT 0;

INSERT OR IGNORE INTO schema_migrations(version, applied_at, checksum)
VALUES ('008_sales_ops', 0, 'sqlite');
