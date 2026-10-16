-- Author: 张晓达
-- Whole receipt/payment void: audit columns + bank movement reverse link.

ALTER TABLE incoming_payments ADD COLUMN voided_at INTEGER NOT NULL DEFAULT 0;
ALTER TABLE incoming_payments ADD COLUMN voided_by INTEGER NOT NULL DEFAULT 0;
ALTER TABLE incoming_payments ADD COLUMN void_reason TEXT NOT NULL DEFAULT '';

ALTER TABLE outgoing_payments ADD COLUMN voided_at INTEGER NOT NULL DEFAULT 0;
ALTER TABLE outgoing_payments ADD COLUMN voided_by INTEGER NOT NULL DEFAULT 0;
ALTER TABLE outgoing_payments ADD COLUMN void_reason TEXT NOT NULL DEFAULT '';

ALTER TABLE bank_cash_movements ADD COLUMN reverse_of_id INTEGER NOT NULL DEFAULT 0;
