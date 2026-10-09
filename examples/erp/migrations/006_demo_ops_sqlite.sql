-- Demo ops: purchase requests, light masters, seed log (SQLite)
-- Author: 张晓达

CREATE TABLE IF NOT EXISTS item_categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    UNIQUE (company_id, code)
);

CREATE TABLE IF NOT EXISTS units_of_measure (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    UNIQUE (company_id, code)
);

CREATE TABLE IF NOT EXISTS employees (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    department TEXT NOT NULL DEFAULT '',
    title TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at INTEGER NOT NULL,
    UNIQUE (company_id, code)
);

CREATE TABLE IF NOT EXISTS purchase_requests (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    doc_no TEXT NOT NULL,
    requester_name TEXT NOT NULL DEFAULT '',
    supplier_id INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL,
    notes TEXT NOT NULL DEFAULT '',
    seed_key TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    UNIQUE (company_id, doc_no)
);

CREATE TABLE IF NOT EXISTS purchase_request_lines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    purchase_request_id INTEGER NOT NULL,
    line_no INTEGER NOT NULL,
    item_id INTEGER NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price_cents INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS demo_seed_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    seed_version INTEGER NOT NULL,
    seed_key TEXT NOT NULL,
    status TEXT NOT NULL,
    detail TEXT NOT NULL DEFAULT '',
    applied_at INTEGER NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_demo_seed_log_key ON demo_seed_log (company_id, seed_key);
