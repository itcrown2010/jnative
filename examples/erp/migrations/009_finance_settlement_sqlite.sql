-- Author: 张晓达
-- Finance settlement sub-ledger: bank cash, adjustments, alloc reverse (integer money).

-- Bank cash balance (sub-ledger; GL journals remain separate / optional).
ALTER TABLE bank_accounts ADD COLUMN balance INTEGER NOT NULL DEFAULT 0;
ALTER TABLE bank_accounts ADD COLUMN allow_overdraft INTEGER NOT NULL DEFAULT 0;

ALTER TABLE ar_invoices ADD COLUMN adjusted_amount INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ar_invoices ADD COLUMN settlement_status TEXT NOT NULL DEFAULT 'OPEN';
ALTER TABLE ar_invoices ADD COLUMN currency_code TEXT NOT NULL DEFAULT 'CNY';

ALTER TABLE ap_invoices ADD COLUMN adjusted_amount INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ap_invoices ADD COLUMN settlement_status TEXT NOT NULL DEFAULT 'OPEN';
ALTER TABLE ap_invoices ADD COLUMN currency_code TEXT NOT NULL DEFAULT 'CNY';

ALTER TABLE incoming_payments ADD COLUMN unallocated_amount INTEGER NOT NULL DEFAULT 0;
ALTER TABLE incoming_payments ADD COLUMN payment_method TEXT NOT NULL DEFAULT '';
ALTER TABLE incoming_payments ADD COLUMN external_ref TEXT NOT NULL DEFAULT '';
ALTER TABLE incoming_payments ADD COLUMN notes TEXT NOT NULL DEFAULT '';
ALTER TABLE incoming_payments ADD COLUMN doc_no TEXT NOT NULL DEFAULT '';
ALTER TABLE incoming_payments ADD COLUMN posted_at INTEGER NOT NULL DEFAULT 0;
ALTER TABLE incoming_payments ADD COLUMN posted_by INTEGER NOT NULL DEFAULT 0;
ALTER TABLE incoming_payments ADD COLUMN created_by INTEGER NOT NULL DEFAULT 0;

ALTER TABLE outgoing_payments ADD COLUMN unallocated_amount INTEGER NOT NULL DEFAULT 0;
ALTER TABLE outgoing_payments ADD COLUMN payment_method TEXT NOT NULL DEFAULT '';
ALTER TABLE outgoing_payments ADD COLUMN external_ref TEXT NOT NULL DEFAULT '';
ALTER TABLE outgoing_payments ADD COLUMN notes TEXT NOT NULL DEFAULT '';
ALTER TABLE outgoing_payments ADD COLUMN doc_no TEXT NOT NULL DEFAULT '';
ALTER TABLE outgoing_payments ADD COLUMN posted_at INTEGER NOT NULL DEFAULT 0;
ALTER TABLE outgoing_payments ADD COLUMN posted_by INTEGER NOT NULL DEFAULT 0;
ALTER TABLE outgoing_payments ADD COLUMN created_by INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS bank_cash_movements (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    bank_account_id INTEGER NOT NULL,
    movement_type TEXT NOT NULL,
    amount INTEGER NOT NULL,
    balance_after INTEGER NOT NULL,
    biz_date INTEGER NOT NULL,
    source_type TEXT NOT NULL,
    source_id INTEGER NOT NULL,
    party_id INTEGER NOT NULL DEFAULT 0,
    notes TEXT NOT NULL DEFAULT '',
    created_by INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    idempotency_key TEXT NOT NULL,
    UNIQUE (company_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS ar_ap_adjustments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    side TEXT NOT NULL,
    invoice_id INTEGER NOT NULL,
    adjust_amount INTEGER NOT NULL,
    reason TEXT NOT NULL DEFAULT '',
    source_type TEXT NOT NULL,
    source_id INTEGER NOT NULL,
    created_by INTEGER NOT NULL DEFAULT 0,
    biz_time INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    idempotency_key TEXT NOT NULL,
    UNIQUE (company_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS ar_settlement_allocs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    payment_id INTEGER NOT NULL,
    invoice_id INTEGER NOT NULL,
    allocated_amount INTEGER NOT NULL,
    status TEXT NOT NULL,
    reverse_of_id INTEGER NOT NULL DEFAULT 0,
    reversed_by_id INTEGER NOT NULL DEFAULT 0,
    reason TEXT NOT NULL DEFAULT '',
    created_by INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    idempotency_key TEXT NOT NULL DEFAULT '',
    UNIQUE (company_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS ap_settlement_allocs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    payment_id INTEGER NOT NULL,
    invoice_id INTEGER NOT NULL,
    allocated_amount INTEGER NOT NULL,
    status TEXT NOT NULL,
    reverse_of_id INTEGER NOT NULL DEFAULT 0,
    reversed_by_id INTEGER NOT NULL DEFAULT 0,
    reason TEXT NOT NULL DEFAULT '',
    created_by INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    idempotency_key TEXT NOT NULL DEFAULT '',
    UNIQUE (company_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS finance_source_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    source_type TEXT NOT NULL,
    source_id INTEGER NOT NULL,
    event_type TEXT NOT NULL,
    ref_id INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    UNIQUE (company_id, source_type, source_id, event_type)
);

-- Partial unique indexes created in ErpDb.ensureFinanceSettlementSchema after tables exist.
