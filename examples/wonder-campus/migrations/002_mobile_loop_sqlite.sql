-- Author: 张晓达
-- Wonder Campus mobile-loop: billing entity, campus grants, attendance/feedback/notify.

CREATE TABLE IF NOT EXISTS wc_billing_entities (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    note TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    UNIQUE (org_id, code)
);

CREATE TABLE IF NOT EXISTS wc_campus_entitlement_grants (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    contract_id INTEGER NOT NULL,
    campus_id INTEGER NOT NULL,
    granted_by INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    UNIQUE (contract_id, campus_id)
);

CREATE TABLE IF NOT EXISTS wc_session_changes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    session_id INTEGER NOT NULL,
    old_start_at INTEGER NOT NULL,
    old_end_at INTEGER NOT NULL,
    new_start_at INTEGER NOT NULL,
    new_end_at INTEGER NOT NULL,
    reason TEXT NOT NULL DEFAULT '',
    actor_user_id INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS wc_makeup_credits (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    leave_id INTEGER NOT NULL UNIQUE,
    student_id INTEGER NOT NULL,
    contract_id INTEGER NOT NULL,
    source_session_id INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'OPEN',
    used_session_id INTEGER NOT NULL DEFAULT 0,
    expires_at INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS wc_notify_tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL DEFAULT '',
    ref_type TEXT NOT NULL DEFAULT '',
    ref_id INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    dedupe_key TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    done_at INTEGER NOT NULL DEFAULT 0,
    UNIQUE (org_id, dedupe_key)
);

CREATE TABLE IF NOT EXISTS wc_media_assets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    owner_user_id INTEGER NOT NULL,
    student_id INTEGER NOT NULL DEFAULT 0,
    session_id INTEGER NOT NULL DEFAULT 0,
    feedback_id INTEGER NOT NULL DEFAULT 0,
    storage_path TEXT NOT NULL,
    mime TEXT NOT NULL DEFAULT 'application/octet-stream',
    size_bytes INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    deleted_at INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS wc_feedback_templates (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    subject TEXT NOT NULL,
    title TEXT NOT NULL,
    body_hint TEXT NOT NULL DEFAULT '',
    UNIQUE (org_id, subject)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_wc_att_active
    ON wc_attendance(session_id, student_id)
    WHERE reversed_at = 0 AND status != 'REVERSED';
