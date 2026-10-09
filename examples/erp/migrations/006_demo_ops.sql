-- Demo ops: purchase requests, light masters, seed log (PostgreSQL)
-- Author: 张晓达

CREATE TABLE IF NOT EXISTS item_categories (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE (company_id, code)
);

CREATE TABLE IF NOT EXISTS units_of_measure (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE (company_id, code)
);

CREATE TABLE IF NOT EXISTS employees (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    department TEXT NOT NULL DEFAULT '',
    title TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at BIGINT NOT NULL,
    UNIQUE (company_id, code)
);

CREATE TABLE IF NOT EXISTS purchase_requests (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    doc_no TEXT NOT NULL,
    requester_name TEXT NOT NULL DEFAULT '',
    supplier_id BIGINT NOT NULL DEFAULT 0,
    status TEXT NOT NULL,
    notes TEXT NOT NULL DEFAULT '',
    seed_key TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    UNIQUE (company_id, doc_no)
);

CREATE TABLE IF NOT EXISTS purchase_request_lines (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    purchase_request_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    item_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price_cents BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS demo_seed_log (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    seed_version INT NOT NULL,
    seed_key TEXT NOT NULL,
    status TEXT NOT NULL,
    detail TEXT NOT NULL DEFAULT '',
    applied_at BIGINT NOT NULL,
    UNIQUE (company_id, seed_key)
);
