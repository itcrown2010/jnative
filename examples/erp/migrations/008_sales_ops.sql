-- Author: 张晓达
-- Sales ops (PostgreSQL).

ALTER TABLE sales_orders ADD COLUMN IF NOT EXISTS warehouse_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sales_orders ADD COLUMN IF NOT EXISTS order_date BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sales_orders ADD COLUMN IF NOT EXISTS delivery_date BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sales_orders ADD COLUMN IF NOT EXISTS notes TEXT NOT NULL DEFAULT '';
ALTER TABLE sales_orders ADD COLUMN IF NOT EXISTS currency TEXT NOT NULL DEFAULT 'CNY';
ALTER TABLE sales_orders ADD COLUMN IF NOT EXISTS approval_status TEXT NOT NULL DEFAULT 'DRAFT';
ALTER TABLE sales_orders ADD COLUMN IF NOT EXISTS fulfillment_status TEXT NOT NULL DEFAULT 'NONE';

ALTER TABLE sales_order_lines ADD COLUMN IF NOT EXISTS returned_qty INT NOT NULL DEFAULT 0;

ALTER TABLE ar_invoices ADD COLUMN IF NOT EXISTS is_provisional INT NOT NULL DEFAULT 0;
ALTER TABLE ar_invoices ADD COLUMN IF NOT EXISTS notes TEXT NOT NULL DEFAULT '';

CREATE UNIQUE INDEX IF NOT EXISTS ux_ar_source_dl
    ON ar_invoices (company_id, source_delivery_id)
    WHERE source_delivery_id IS NOT NULL AND source_delivery_id > 0;

CREATE TABLE IF NOT EXISTS inventory_reservations (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    sales_order_id BIGINT NOT NULL,
    sales_order_line_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    quantity_consumed INT NOT NULL DEFAULT 0,
    quantity_released INT NOT NULL DEFAULT 0,
    status TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_inv_res_wh_item
    ON inventory_reservations (company_id, warehouse_id, item_id, status);

CREATE INDEX IF NOT EXISTS idx_inv_res_so_line
    ON inventory_reservations (company_id, sales_order_line_id, status);

CREATE TABLE IF NOT EXISTS sales_returns (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    doc_no TEXT NOT NULL,
    sales_order_id BIGINT NOT NULL,
    delivery_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    status TEXT NOT NULL,
    resellable INT NOT NULL DEFAULT 1,
    idempotency_key TEXT NOT NULL,
    notes TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL,
    UNIQUE (company_id, doc_no),
    UNIQUE (company_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS sales_return_lines (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    sales_return_id BIGINT NOT NULL,
    delivery_line_id BIGINT NOT NULL,
    sales_order_line_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    quantity INT NOT NULL
);

ALTER TABLE delivery_lines ADD COLUMN IF NOT EXISTS returned_qty INT NOT NULL DEFAULT 0;

INSERT INTO schema_migrations(version, applied_at, checksum)
VALUES ('008_sales_ops', 0, 'pg')
ON CONFLICT (version) DO NOTHING;
