-- Author: 张晓达
-- Pilot baseline: book isolation, inventory CAS, opening docs, reverse links, wizard state.
-- Applied idempotently via ErpDb.ensurePilotSchema (ALTER may already exist).

CREATE TABLE IF NOT EXISTS demo_seed_meta (
    company_id BIGINT NOT NULL PRIMARY KEY REFERENCES companies(id),
    seed_version INT NOT NULL,
    applied_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS setup_wizard_state (
    company_id BIGINT NOT NULL PRIMARY KEY REFERENCES companies(id),
    step_code TEXT NOT NULL DEFAULT 'COMPANY',
    payload_json TEXT NOT NULL DEFAULT '{}',
    completed INT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL,
    updated_by BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS opening_stock_docs (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    doc_no TEXT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    business_date BIGINT NOT NULL,
    status TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    notes TEXT NOT NULL DEFAULT '',
    created_by BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    posted_at BIGINT NOT NULL DEFAULT 0,
    UNIQUE (company_id, doc_no),
    UNIQUE (company_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS opening_stock_lines (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    doc_id BIGINT NOT NULL REFERENCES opening_stock_docs(id),
    item_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_cost_amount BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS opening_ar_docs (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    doc_no TEXT NOT NULL,
    customer_id BIGINT NOT NULL,
    business_date BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    status TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    notes TEXT NOT NULL DEFAULT '',
    created_by BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    posted_at BIGINT NOT NULL DEFAULT 0,
    UNIQUE (company_id, doc_no),
    UNIQUE (company_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS opening_ap_docs (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    doc_no TEXT NOT NULL,
    supplier_id BIGINT NOT NULL,
    business_date BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    status TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    notes TEXT NOT NULL DEFAULT '',
    created_by BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    posted_at BIGINT NOT NULL DEFAULT 0,
    UNIQUE (company_id, doc_no),
    UNIQUE (company_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS import_batches (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    batch_code TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    status TEXT NOT NULL,
    total_rows INT NOT NULL DEFAULT 0,
    ok_rows INT NOT NULL DEFAULT 0,
    error_rows INT NOT NULL DEFAULT 0,
    error_report TEXT NOT NULL DEFAULT '',
    created_by BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    UNIQUE (company_id, batch_code)
);
