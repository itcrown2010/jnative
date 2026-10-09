-- Author: 张晓达
-- Finance 1.2 schema (PostgreSQL). Money = integer currency units (no float).

ALTER TABLE items ADD COLUMN IF NOT EXISTS unit_cost_amount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE inventory_balances ADD COLUMN IF NOT EXISTS value_amount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE inventory_transactions ADD COLUMN IF NOT EXISTS unit_cost_amount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE inventory_transactions ADD COLUMN IF NOT EXISTS value_delta_amount BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS gl_accounts (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_code TEXT NOT NULL,
    account_name TEXT NOT NULL,
    account_type TEXT NOT NULL,
    parent_account_id BIGINT,
    posting_allowed INT NOT NULL DEFAULT 1,
    active INT NOT NULL DEFAULT 1,
    UNIQUE (company_id, account_code)
);

CREATE TABLE IF NOT EXISTS fiscal_periods (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    fiscal_year INT NOT NULL,
    period_number INT NOT NULL,
    start_date BIGINT NOT NULL,
    end_date BIGINT NOT NULL,
    status TEXT NOT NULL,
    UNIQUE (company_id, fiscal_year, period_number)
);

CREATE TABLE IF NOT EXISTS account_mappings (
    company_id BIGINT NOT NULL,
    mapping_key TEXT NOT NULL,
    account_id BIGINT NOT NULL REFERENCES gl_accounts(id),
    PRIMARY KEY (company_id, mapping_key)
);

CREATE TABLE IF NOT EXISTS journal_entries (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    journal_number TEXT NOT NULL,
    posting_date BIGINT NOT NULL,
    document_date BIGINT NOT NULL,
    source_type TEXT NOT NULL,
    source_id BIGINT NOT NULL DEFAULT 0,
    status TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    created_by BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    reversed_journal_id BIGINT,
    UNIQUE (company_id, journal_number)
);

CREATE TABLE IF NOT EXISTS journal_entry_lines (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    journal_entry_id BIGINT NOT NULL REFERENCES journal_entries(id),
    line_no INT NOT NULL,
    account_id BIGINT NOT NULL REFERENCES gl_accounts(id),
    debit_amount BIGINT NOT NULL DEFAULT 0,
    credit_amount BIGINT NOT NULL DEFAULT 0,
    business_partner_id BIGINT,
    description TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS bank_accounts (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_name TEXT NOT NULL,
    currency TEXT NOT NULL DEFAULT 'USD',
    ledger_account_id BIGINT NOT NULL REFERENCES gl_accounts(id),
    active INT NOT NULL DEFAULT 1,
    UNIQUE (company_id, account_name)
);

CREATE TABLE IF NOT EXISTS ar_invoices (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    invoice_number TEXT NOT NULL,
    invoice_date BIGINT NOT NULL,
    due_date BIGINT NOT NULL,
    currency TEXT NOT NULL DEFAULT 'USD',
    total_amount BIGINT NOT NULL,
    paid_amount BIGINT NOT NULL DEFAULT 0,
    open_amount BIGINT NOT NULL,
    status TEXT NOT NULL,
    source_delivery_id BIGINT,
    journal_entry_id BIGINT,
    created_at BIGINT NOT NULL,
    UNIQUE (company_id, invoice_number)
);

CREATE TABLE IF NOT EXISTS ar_invoice_lines (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    ar_invoice_id BIGINT NOT NULL REFERENCES ar_invoices(id),
    line_no INT NOT NULL,
    item_id BIGINT,
    description TEXT NOT NULL DEFAULT '',
    quantity INT NOT NULL DEFAULT 0,
    unit_price_amount BIGINT NOT NULL DEFAULT 0,
    line_amount BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS incoming_payments (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    payment_date BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    allocated_amount BIGINT NOT NULL DEFAULT 0,
    bank_account_id BIGINT NOT NULL REFERENCES bank_accounts(id),
    status TEXT NOT NULL,
    journal_entry_id BIGINT,
    idempotency_key TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE (company_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS incoming_payment_allocations (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL REFERENCES incoming_payments(id),
    invoice_id BIGINT NOT NULL REFERENCES ar_invoices(id),
    allocated_amount BIGINT NOT NULL,
    UNIQUE (payment_id, invoice_id)
);

CREATE TABLE IF NOT EXISTS ap_invoices (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    invoice_number TEXT NOT NULL,
    invoice_date BIGINT NOT NULL,
    due_date BIGINT NOT NULL,
    currency TEXT NOT NULL DEFAULT 'USD',
    total_amount BIGINT NOT NULL,
    paid_amount BIGINT NOT NULL DEFAULT 0,
    open_amount BIGINT NOT NULL,
    status TEXT NOT NULL,
    source_gr_id BIGINT,
    journal_entry_id BIGINT,
    created_at BIGINT NOT NULL,
    UNIQUE (company_id, invoice_number)
);

CREATE TABLE IF NOT EXISTS ap_invoice_lines (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    ap_invoice_id BIGINT NOT NULL REFERENCES ap_invoices(id),
    line_no INT NOT NULL,
    item_id BIGINT,
    description TEXT NOT NULL DEFAULT '',
    quantity INT NOT NULL DEFAULT 0,
    unit_price_amount BIGINT NOT NULL DEFAULT 0,
    line_amount BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS outgoing_payments (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    payment_date BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    allocated_amount BIGINT NOT NULL DEFAULT 0,
    bank_account_id BIGINT NOT NULL REFERENCES bank_accounts(id),
    status TEXT NOT NULL,
    journal_entry_id BIGINT,
    idempotency_key TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE (company_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS outgoing_payment_allocations (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL REFERENCES outgoing_payments(id),
    invoice_id BIGINT NOT NULL REFERENCES ap_invoices(id),
    allocated_amount BIGINT NOT NULL,
    UNIQUE (payment_id, invoice_id)
);

CREATE INDEX IF NOT EXISTS idx_je_company_date ON journal_entries(company_id, posting_date);
CREATE INDEX IF NOT EXISTS idx_jel_account ON journal_entry_lines(company_id, account_id);
CREATE INDEX IF NOT EXISTS idx_ar_open ON ar_invoices(company_id, status, due_date);
CREATE INDEX IF NOT EXISTS idx_ap_open ON ap_invoices(company_id, status, due_date);
