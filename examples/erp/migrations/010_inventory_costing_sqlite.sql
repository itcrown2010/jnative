-- Author: 张晓达
-- Moving-average inventory costing: delivery line COGS, cost events, biz vs post time.

ALTER TABLE delivery_lines ADD COLUMN unit_cost_amount INTEGER NOT NULL DEFAULT 0;
ALTER TABLE delivery_lines ADD COLUMN cogs_amount INTEGER NOT NULL DEFAULT 0;
ALTER TABLE delivery_lines ADD COLUMN cogs_returned_amount INTEGER NOT NULL DEFAULT 0;

ALTER TABLE goods_receipt_lines ADD COLUMN unit_cost_amount INTEGER NOT NULL DEFAULT 0;
ALTER TABLE goods_receipt_lines ADD COLUMN line_value_amount INTEGER NOT NULL DEFAULT 0;

ALTER TABLE inventory_transactions ADD COLUMN biz_date INTEGER NOT NULL DEFAULT 0;
ALTER TABLE inventory_transactions ADD COLUMN posted_by INTEGER NOT NULL DEFAULT 0;
ALTER TABLE inventory_transactions ADD COLUMN notes TEXT NOT NULL DEFAULT '';

CREATE TABLE IF NOT EXISTS inventory_cost_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    warehouse_id INTEGER NOT NULL,
    item_id INTEGER NOT NULL,
    event_type TEXT NOT NULL,
    quantity_delta INTEGER NOT NULL,
    unit_cost_amount INTEGER NOT NULL DEFAULT 0,
    value_delta_amount INTEGER NOT NULL,
    qty_before INTEGER NOT NULL DEFAULT 0,
    value_before INTEGER NOT NULL DEFAULT 0,
    qty_after INTEGER NOT NULL DEFAULT 0,
    value_after INTEGER NOT NULL DEFAULT 0,
    avg_cost_after INTEGER NOT NULL DEFAULT 0,
    source_type TEXT NOT NULL,
    source_id INTEGER NOT NULL,
    source_line_id INTEGER NOT NULL DEFAULT 0,
    related_event_id INTEGER NOT NULL DEFAULT 0,
    biz_date INTEGER NOT NULL,
    posted_at INTEGER NOT NULL,
    posted_by INTEGER NOT NULL DEFAULT 0,
    reason TEXT NOT NULL DEFAULT '',
    idempotency_key TEXT NOT NULL DEFAULT '',
    UNIQUE (company_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_cost_events_item
    ON inventory_cost_events (company_id, warehouse_id, item_id, id);
