-- Author: 张晓达
-- Management GL ledger (PostgreSQL). Money = integer currency units.
-- Policy MGMT_DIRECT_ACCRUAL is for internal management accounting only.

ALTER TABLE companies ADD COLUMN IF NOT EXISTS gl_policy TEXT NOT NULL DEFAULT 'PROVISIONAL_CLEARING';
ALTER TABLE companies ADD COLUMN IF NOT EXISTS functional_currency TEXT NOT NULL DEFAULT 'CNY';

ALTER TABLE gl_accounts ADD COLUMN IF NOT EXISTS normal_balance TEXT NOT NULL DEFAULT 'DEBIT';
ALTER TABLE gl_accounts ADD COLUMN IF NOT EXISTS is_control INTEGER NOT NULL DEFAULT 0;
ALTER TABLE gl_accounts ADD COLUMN IF NOT EXISTS require_partner INTEGER NOT NULL DEFAULT 0;
ALTER TABLE gl_accounts ADD COLUMN IF NOT EXISTS require_warehouse INTEGER NOT NULL DEFAULT 0;

ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS approved_by BIGINT NOT NULL DEFAULT 0;
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS approved_at BIGINT NOT NULL DEFAULT 0;
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS posted_by BIGINT NOT NULL DEFAULT 0;
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS posted_at BIGINT NOT NULL DEFAULT 0;
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS rejected_reason TEXT NOT NULL DEFAULT '';
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS event_type TEXT NOT NULL DEFAULT '';
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS fiscal_period_id BIGINT NOT NULL DEFAULT 0;

ALTER TABLE journal_entry_lines ADD COLUMN IF NOT EXISTS warehouse_id BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS gl_posting_events (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    event_type TEXT NOT NULL,
    source_type TEXT NOT NULL,
    source_id BIGINT NOT NULL,
    source_line_id BIGINT NOT NULL DEFAULT 0,
    status TEXT NOT NULL,
    journal_entry_id BIGINT NOT NULL DEFAULT 0,
    amount BIGINT NOT NULL DEFAULT 0,
    partner_id BIGINT NOT NULL DEFAULT 0,
    warehouse_id BIGINT NOT NULL DEFAULT 0,
    biz_date BIGINT NOT NULL,
    payload_json TEXT NOT NULL DEFAULT '',
    error_code TEXT NOT NULL DEFAULT '',
    error_message TEXT NOT NULL DEFAULT '',
    retry_count INT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    posted_at BIGINT NOT NULL DEFAULT 0,
    UNIQUE (company_id, event_type, source_type, source_id, source_line_id)
);

CREATE INDEX IF NOT EXISTS idx_gl_events_status ON gl_posting_events(company_id, status);
CREATE INDEX IF NOT EXISTS idx_gl_events_source ON gl_posting_events(company_id, source_type, source_id);
CREATE INDEX IF NOT EXISTS idx_je_period ON journal_entries(company_id, fiscal_period_id, status);
