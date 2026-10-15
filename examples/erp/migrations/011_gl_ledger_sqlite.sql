-- Author: 张晓达
-- Management GL ledger (SQLite). Money = integer currency units.

ALTER TABLE companies ADD COLUMN gl_policy TEXT NOT NULL DEFAULT 'PROVISIONAL_CLEARING';
ALTER TABLE companies ADD COLUMN functional_currency TEXT NOT NULL DEFAULT 'CNY';

ALTER TABLE gl_accounts ADD COLUMN normal_balance TEXT NOT NULL DEFAULT 'DEBIT';
ALTER TABLE gl_accounts ADD COLUMN is_control INTEGER NOT NULL DEFAULT 0;
ALTER TABLE gl_accounts ADD COLUMN require_partner INTEGER NOT NULL DEFAULT 0;
ALTER TABLE gl_accounts ADD COLUMN require_warehouse INTEGER NOT NULL DEFAULT 0;

ALTER TABLE journal_entries ADD COLUMN approved_by INTEGER NOT NULL DEFAULT 0;
ALTER TABLE journal_entries ADD COLUMN approved_at INTEGER NOT NULL DEFAULT 0;
ALTER TABLE journal_entries ADD COLUMN posted_by INTEGER NOT NULL DEFAULT 0;
ALTER TABLE journal_entries ADD COLUMN posted_at INTEGER NOT NULL DEFAULT 0;
ALTER TABLE journal_entries ADD COLUMN rejected_reason TEXT NOT NULL DEFAULT '';
ALTER TABLE journal_entries ADD COLUMN event_type TEXT NOT NULL DEFAULT '';
ALTER TABLE journal_entries ADD COLUMN fiscal_period_id INTEGER NOT NULL DEFAULT 0;

ALTER TABLE journal_entry_lines ADD COLUMN warehouse_id INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS gl_posting_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    event_type TEXT NOT NULL,
    source_type TEXT NOT NULL,
    source_id INTEGER NOT NULL,
    source_line_id INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL,
    journal_entry_id INTEGER NOT NULL DEFAULT 0,
    amount INTEGER NOT NULL DEFAULT 0,
    partner_id INTEGER NOT NULL DEFAULT 0,
    warehouse_id INTEGER NOT NULL DEFAULT 0,
    biz_date INTEGER NOT NULL,
    payload_json TEXT NOT NULL DEFAULT '',
    error_code TEXT NOT NULL DEFAULT '',
    error_message TEXT NOT NULL DEFAULT '',
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    posted_at INTEGER NOT NULL DEFAULT 0,
    UNIQUE (company_id, event_type, source_type, source_id, source_line_id)
);

CREATE INDEX IF NOT EXISTS idx_gl_events_status ON gl_posting_events(company_id, status);
CREATE INDEX IF NOT EXISTS idx_gl_events_source ON gl_posting_events(company_id, source_type, source_id);
CREATE INDEX IF NOT EXISTS idx_je_period ON journal_entries(company_id, fiscal_period_id, status);
