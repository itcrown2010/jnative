-- Author: 张晓达
-- Finance 1.2 schema (SQLite mirror). Money = integer currency units.

CREATE TABLE IF NOT EXISTS gl_accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    account_code TEXT NOT NULL,
    account_name TEXT NOT NULL,
    account_type TEXT NOT NULL,
    parent_account_id INTEGER,
    posting_allowed INTEGER NOT NULL DEFAULT 1,
    active INTEGER NOT NULL DEFAULT 1,
    UNIQUE (company_id, account_code)
);

CREATE TABLE IF NOT EXISTS fiscal_periods (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    fiscal_year INTEGER NOT NULL,
    period_number INTEGER NOT NULL,
    start_date INTEGER NOT NULL,
    end_date INTEGER NOT NULL,
    status TEXT NOT NULL,
    UNIQUE (company_id, fiscal_year, period_number)
);

CREATE TABLE IF NOT EXISTS account_mappings (
    company_id INTEGER NOT NULL,
    mapping_key TEXT NOT NULL,
    account_id INTEGER NOT NULL,
    PRIMARY KEY (company_id, mapping_key)
);

CREATE TABLE IF NOT EXISTS journal_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    journal_number TEXT NOT NULL,
    posting_date INTEGER NOT NULL,
    document_date INTEGER NOT NULL,
    source_type TEXT NOT NULL,
    source_id INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    created_by INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    reversed_journal_id INTEGER,
    UNIQUE (company_id, journal_number)
);

CREATE TABLE IF NOT EXISTS journal_entry_lines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    journal_entry_id INTEGER NOT NULL,
    line_no INTEGER NOT NULL,
    account_id INTEGER NOT NULL,
    debit_amount INTEGER NOT NULL DEFAULT 0,
    credit_amount INTEGER NOT NULL DEFAULT 0,
    business_partner_id INTEGER,
    description TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS bank_accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    account_name TEXT NOT NULL,
    currency TEXT NOT NULL DEFAULT 'USD',
    ledger_account_id INTEGER NOT NULL,
    active INTEGER NOT NULL DEFAULT 1,
    UNIQUE (company_id, account_name)
);

CREATE TABLE IF NOT EXISTS ar_invoices (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    customer_id INTEGER NOT NULL,
    invoice_number TEXT NOT NULL,
    invoice_date INTEGER NOT NULL,
    due_date INTEGER NOT NULL,
    currency TEXT NOT NULL DEFAULT 'USD',
    total_amount INTEGER NOT NULL,
    paid_amount INTEGER NOT NULL DEFAULT 0,
    open_amount INTEGER NOT NULL,
    status TEXT NOT NULL,
    source_delivery_id INTEGER,
    journal_entry_id INTEGER,
    created_at INTEGER NOT NULL,
    UNIQUE (company_id, invoice_number)
);

CREATE TABLE IF NOT EXISTS ar_invoice_lines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    ar_invoice_id INTEGER NOT NULL,
    line_no INTEGER NOT NULL,
    item_id INTEGER,
    description TEXT NOT NULL DEFAULT '',
    quantity INTEGER NOT NULL DEFAULT 0,
    unit_price_amount INTEGER NOT NULL DEFAULT 0,
    line_amount INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS incoming_payments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    customer_id INTEGER NOT NULL,
    payment_date INTEGER NOT NULL,
    amount INTEGER NOT NULL,
    allocated_amount INTEGER NOT NULL DEFAULT 0,
    bank_account_id INTEGER NOT NULL,
    status TEXT NOT NULL,
    journal_entry_id INTEGER,
    idempotency_key TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    UNIQUE (company_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS incoming_payment_allocations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    payment_id INTEGER NOT NULL,
    invoice_id INTEGER NOT NULL,
    allocated_amount INTEGER NOT NULL,
    UNIQUE (payment_id, invoice_id)
);

CREATE TABLE IF NOT EXISTS ap_invoices (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    supplier_id INTEGER NOT NULL,
    invoice_number TEXT NOT NULL,
    invoice_date INTEGER NOT NULL,
    due_date INTEGER NOT NULL,
    currency TEXT NOT NULL DEFAULT 'USD',
    total_amount INTEGER NOT NULL,
    paid_amount INTEGER NOT NULL DEFAULT 0,
    open_amount INTEGER NOT NULL,
    status TEXT NOT NULL,
    source_gr_id INTEGER,
    journal_entry_id INTEGER,
    created_at INTEGER NOT NULL,
    UNIQUE (company_id, invoice_number)
);

CREATE TABLE IF NOT EXISTS ap_invoice_lines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    ap_invoice_id INTEGER NOT NULL,
    line_no INTEGER NOT NULL,
    item_id INTEGER,
    description TEXT NOT NULL DEFAULT '',
    quantity INTEGER NOT NULL DEFAULT 0,
    unit_price_amount INTEGER NOT NULL DEFAULT 0,
    line_amount INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS outgoing_payments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    supplier_id INTEGER NOT NULL,
    payment_date INTEGER NOT NULL,
    amount INTEGER NOT NULL,
    allocated_amount INTEGER NOT NULL DEFAULT 0,
    bank_account_id INTEGER NOT NULL,
    status TEXT NOT NULL,
    journal_entry_id INTEGER,
    idempotency_key TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    UNIQUE (company_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS outgoing_payment_allocations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    payment_id INTEGER NOT NULL,
    invoice_id INTEGER NOT NULL,
    allocated_amount INTEGER NOT NULL,
    UNIQUE (payment_id, invoice_id)
);
