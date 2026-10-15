-- Author: 张晓达
-- Finance settlement sub-ledger (PostgreSQL). Money = integer currency units.

ALTER TABLE bank_accounts ADD COLUMN IF NOT EXISTS balance BIGINT NOT NULL DEFAULT 0;
ALTER TABLE bank_accounts ADD COLUMN IF NOT EXISTS allow_overdraft INT NOT NULL DEFAULT 0;

ALTER TABLE ar_invoices ADD COLUMN IF NOT EXISTS adjusted_amount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ar_invoices ADD COLUMN IF NOT EXISTS settlement_status TEXT NOT NULL DEFAULT 'OPEN';
ALTER TABLE ar_invoices ADD COLUMN IF NOT EXISTS currency_code TEXT NOT NULL DEFAULT 'CNY';

ALTER TABLE ap_invoices ADD COLUMN IF NOT EXISTS adjusted_amount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ap_invoices ADD COLUMN IF NOT EXISTS settlement_status TEXT NOT NULL DEFAULT 'OPEN';
ALTER TABLE ap_invoices ADD COLUMN IF NOT EXISTS currency_code TEXT NOT NULL DEFAULT 'CNY';

ALTER TABLE incoming_payments ADD COLUMN IF NOT EXISTS unallocated_amount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE incoming_payments ADD COLUMN IF NOT EXISTS payment_method TEXT NOT NULL DEFAULT '';
ALTER TABLE incoming_payments ADD COLUMN IF NOT EXISTS external_ref TEXT NOT NULL DEFAULT '';
ALTER TABLE incoming_payments ADD COLUMN IF NOT EXISTS notes TEXT NOT NULL DEFAULT '';
ALTER TABLE incoming_payments ADD COLUMN IF NOT EXISTS doc_no TEXT NOT NULL DEFAULT '';
ALTER TABLE incoming_payments ADD COLUMN IF NOT EXISTS posted_at BIGINT NOT NULL DEFAULT 0;
ALTER TABLE incoming_payments ADD COLUMN IF NOT EXISTS posted_by BIGINT NOT NULL DEFAULT 0;
ALTER TABLE incoming_payments ADD COLUMN IF NOT EXISTS created_by BIGINT NOT NULL DEFAULT 0;

ALTER TABLE outgoing_payments ADD COLUMN IF NOT EXISTS unallocated_amount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE outgoing_payments ADD COLUMN IF NOT EXISTS payment_method TEXT NOT NULL DEFAULT '';
ALTER TABLE outgoing_payments ADD COLUMN IF NOT EXISTS external_ref TEXT NOT NULL DEFAULT '';
ALTER TABLE outgoing_payments ADD COLUMN IF NOT EXISTS notes TEXT NOT NULL DEFAULT '';
ALTER TABLE outgoing_payments ADD COLUMN IF NOT EXISTS doc_no TEXT NOT NULL DEFAULT '';
ALTER TABLE outgoing_payments ADD COLUMN IF NOT EXISTS posted_at BIGINT NOT NULL DEFAULT 0;
ALTER TABLE outgoing_payments ADD COLUMN IF NOT EXISTS posted_by BIGINT NOT NULL DEFAULT 0;
ALTER TABLE outgoing_payments ADD COLUMN IF NOT EXISTS created_by BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS bank_cash_movements (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    bank_account_id BIGINT NOT NULL,
    movement_type TEXT NOT NULL,
    amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    biz_date BIGINT NOT NULL,
    source_type TEXT NOT NULL,
    source_id BIGINT NOT NULL,
    party_id BIGINT NOT NULL DEFAULT 0,
    notes TEXT NOT NULL DEFAULT '',
    created_by BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    idempotency_key TEXT NOT NULL,
    UNIQUE (company_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS ar_ap_adjustments (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    side TEXT NOT NULL,
    invoice_id BIGINT NOT NULL,
    adjust_amount BIGINT NOT NULL,
    reason TEXT NOT NULL DEFAULT '',
    source_type TEXT NOT NULL,
    source_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    biz_time BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    idempotency_key TEXT NOT NULL,
    UNIQUE (company_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS ar_settlement_allocs (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL,
    invoice_id BIGINT NOT NULL,
    allocated_amount BIGINT NOT NULL,
    status TEXT NOT NULL,
    reverse_of_id BIGINT NOT NULL DEFAULT 0,
    reversed_by_id BIGINT NOT NULL DEFAULT 0,
    reason TEXT NOT NULL DEFAULT '',
    created_by BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    idempotency_key TEXT NOT NULL DEFAULT '',
    UNIQUE (company_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS ap_settlement_allocs (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL,
    invoice_id BIGINT NOT NULL,
    allocated_amount BIGINT NOT NULL,
    status TEXT NOT NULL,
    reverse_of_id BIGINT NOT NULL DEFAULT 0,
    reversed_by_id BIGINT NOT NULL DEFAULT 0,
    reason TEXT NOT NULL DEFAULT '',
    created_by BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    idempotency_key TEXT NOT NULL DEFAULT '',
    UNIQUE (company_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS finance_source_events (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    source_type TEXT NOT NULL,
    source_id BIGINT NOT NULL,
    event_type TEXT NOT NULL,
    ref_id BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    UNIQUE (company_id, source_type, source_id, event_type)
);
