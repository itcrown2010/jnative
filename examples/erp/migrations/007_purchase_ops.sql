-- Author: 张晓达
-- Purchase / inventory ops (PostgreSQL).

ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS warehouse_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS order_date BIGINT NOT NULL DEFAULT 0;
ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS expected_date BIGINT NOT NULL DEFAULT 0;
ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS notes TEXT NOT NULL DEFAULT '';
ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS currency TEXT NOT NULL DEFAULT 'CNY';

ALTER TABLE items ADD COLUMN IF NOT EXISTS min_qty INT NOT NULL DEFAULT 0;

ALTER TABLE ap_invoices ADD COLUMN IF NOT EXISTS is_provisional INT NOT NULL DEFAULT 0;
ALTER TABLE ap_invoices ADD COLUMN IF NOT EXISTS notes TEXT NOT NULL DEFAULT '';

CREATE UNIQUE INDEX IF NOT EXISTS ux_ap_source_gr
    ON ap_invoices (company_id, source_gr_id)
    WHERE source_gr_id IS NOT NULL AND source_gr_id > 0;

CREATE TABLE IF NOT EXISTS inventory_adjustments (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    doc_no TEXT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    quantity_delta INT NOT NULL,
    reason TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    UNIQUE (company_id, doc_no),
    UNIQUE (company_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS inventory_counts (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    doc_no TEXT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    status TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    notes TEXT NOT NULL DEFAULT '',
    created_by BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    posted_at BIGINT NOT NULL DEFAULT 0,
    UNIQUE (company_id, doc_no),
    UNIQUE (company_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS inventory_count_lines (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    inventory_count_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    system_qty INT NOT NULL,
    counted_qty INT NOT NULL,
    variance_qty INT NOT NULL
);

INSERT INTO schema_migrations(version, applied_at, checksum)
VALUES ('007_purchase_ops', 0, 'pg')
ON CONFLICT (version) DO NOTHING;
