-- Author: 张晓达
-- Whole receipt/payment void (PostgreSQL).

ALTER TABLE incoming_payments ADD COLUMN IF NOT EXISTS voided_at BIGINT NOT NULL DEFAULT 0;
ALTER TABLE incoming_payments ADD COLUMN IF NOT EXISTS voided_by BIGINT NOT NULL DEFAULT 0;
ALTER TABLE incoming_payments ADD COLUMN IF NOT EXISTS void_reason TEXT NOT NULL DEFAULT '';

ALTER TABLE outgoing_payments ADD COLUMN IF NOT EXISTS voided_at BIGINT NOT NULL DEFAULT 0;
ALTER TABLE outgoing_payments ADD COLUMN IF NOT EXISTS voided_by BIGINT NOT NULL DEFAULT 0;
ALTER TABLE outgoing_payments ADD COLUMN IF NOT EXISTS void_reason TEXT NOT NULL DEFAULT '';

ALTER TABLE bank_cash_movements ADD COLUMN IF NOT EXISTS reverse_of_id BIGINT NOT NULL DEFAULT 0;
