-- PostgreSQL schema for JNative ERP Phase-1
CREATE TABLE IF NOT EXISTS companies (
    id BIGSERIAL PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    email TEXT NOT NULL,
    display_name TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'user',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    UNIQUE (company_id, email)
);

CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    UNIQUE (company_id, code)
);

CREATE TABLE IF NOT EXISTS permissions (
    id BIGSERIAL PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id),
    role_id BIGINT NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id BIGINT NOT NULL REFERENCES roles(id),
    permission_id BIGINT NOT NULL REFERENCES permissions(id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    user_id BIGINT,
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id BIGINT,
    detail TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS business_partners (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    kind TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE (company_id, code)
);

CREATE TABLE IF NOT EXISTS items (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    sku TEXT NOT NULL,
    name TEXT NOT NULL,
    unit_price_cents BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    UNIQUE (company_id, sku)
);

CREATE TABLE IF NOT EXISTS warehouses (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE (company_id, code)
);

CREATE TABLE IF NOT EXISTS purchase_orders (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    doc_no TEXT NOT NULL,
    supplier_id BIGINT NOT NULL REFERENCES business_partners(id),
    status TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    UNIQUE (company_id, doc_no)
);

CREATE TABLE IF NOT EXISTS purchase_order_lines (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders(id),
    line_no INTEGER NOT NULL,
    item_id BIGINT NOT NULL REFERENCES items(id),
    quantity INTEGER NOT NULL,
    received_qty INTEGER NOT NULL DEFAULT 0,
    unit_price_cents BIGINT NOT NULL,
    UNIQUE (purchase_order_id, line_no)
);

CREATE TABLE IF NOT EXISTS sales_orders (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    doc_no TEXT NOT NULL,
    customer_id BIGINT NOT NULL REFERENCES business_partners(id),
    status TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    UNIQUE (company_id, doc_no)
);

CREATE TABLE IF NOT EXISTS sales_order_lines (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    sales_order_id BIGINT NOT NULL REFERENCES sales_orders(id),
    line_no INTEGER NOT NULL,
    item_id BIGINT NOT NULL REFERENCES items(id),
    quantity INTEGER NOT NULL,
    delivered_qty INTEGER NOT NULL DEFAULT 0,
    unit_price_cents BIGINT NOT NULL,
    UNIQUE (sales_order_id, line_no)
);

CREATE TABLE IF NOT EXISTS goods_receipts (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    doc_no TEXT NOT NULL,
    purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders(id),
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(id),
    status TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE (company_id, doc_no)
);

CREATE TABLE IF NOT EXISTS goods_receipt_lines (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    goods_receipt_id BIGINT NOT NULL REFERENCES goods_receipts(id),
    purchase_order_line_id BIGINT NOT NULL REFERENCES purchase_order_lines(id),
    item_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS deliveries (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    doc_no TEXT NOT NULL,
    sales_order_id BIGINT NOT NULL REFERENCES sales_orders(id),
    warehouse_id BIGINT NOT NULL REFERENCES warehouses(id),
    status TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE (company_id, doc_no)
);

CREATE TABLE IF NOT EXISTS delivery_lines (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    delivery_id BIGINT NOT NULL REFERENCES deliveries(id),
    sales_order_line_id BIGINT NOT NULL REFERENCES sales_order_lines(id),
    item_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS inventory_transactions (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    txn_type TEXT NOT NULL,
    quantity_delta INTEGER NOT NULL,
    ref_doc_type TEXT NOT NULL,
    ref_doc_id BIGINT NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS inventory_balances (
    company_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    quantity_on_hand INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (company_id, warehouse_id, item_id)
);

CREATE TABLE IF NOT EXISTS document_references (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    ref_kind TEXT NOT NULL,
    from_doc_type TEXT NOT NULL,
    from_doc_id BIGINT NOT NULL,
    to_doc_type TEXT NOT NULL,
    to_doc_id BIGINT NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS idempotency_keys (
    company_id BIGINT NOT NULL,
    idempotency_key TEXT NOT NULL,
    operation TEXT NOT NULL,
    response_json TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (company_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS number_sequences (
    company_id BIGINT NOT NULL,
    seq_name TEXT NOT NULL,
    next_val BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (company_id, seq_name)
);

CREATE INDEX IF NOT EXISTS idx_po_company ON purchase_orders(company_id, status);
CREATE INDEX IF NOT EXISTS idx_so_company ON sales_orders(company_id, status);
CREATE INDEX IF NOT EXISTS idx_inv_bal ON inventory_balances(company_id, warehouse_id);
