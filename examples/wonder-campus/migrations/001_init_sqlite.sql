-- Author: 张晓达
-- Wonder Campus schema (SQLite). Money = cents INTEGER. Lessons = lesson_milli INTEGER (1000=1课时).

CREATE TABLE IF NOT EXISTS schema_migrations (
    version TEXT PRIMARY KEY,
    applied_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS wc_orgs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS wc_campuses (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    UNIQUE (org_id, code)
);

CREATE TABLE IF NOT EXISTS wc_users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    login TEXT NOT NULL,
    display_name TEXT NOT NULL,
    password_hash TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    UNIQUE (org_id, login)
);

CREATE TABLE IF NOT EXISTS wc_user_identities (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    org_id INTEGER NOT NULL,
    identity_kind TEXT NOT NULL,
    campus_id INTEGER NOT NULL DEFAULT 0,
    UNIQUE (user_id, identity_kind, campus_id)
);

CREATE TABLE IF NOT EXISTS wc_staff_campus (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    campus_id INTEGER NOT NULL,
    role_code TEXT NOT NULL,
    UNIQUE (user_id, campus_id, role_code)
);

CREATE TABLE IF NOT EXISTS wc_students (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    campus_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    age_band TEXT NOT NULL DEFAULT 'CHILD',
    birth_year INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS wc_guardians (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL DEFAULT 0,
    name TEXT NOT NULL,
    phone TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS wc_student_guardians (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id INTEGER NOT NULL,
    guardian_id INTEGER NOT NULL,
    relation TEXT NOT NULL DEFAULT 'PARENT',
    UNIQUE (student_id, guardian_id)
);

CREATE TABLE IF NOT EXISTS wc_courses (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    subject TEXT NOT NULL,
    UNIQUE (org_id, code)
);

CREATE TABLE IF NOT EXISTS wc_classes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    campus_id INTEGER NOT NULL,
    course_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    teacher_user_id INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS wc_rooms (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    campus_id INTEGER NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    UNIQUE (campus_id, code)
);

CREATE TABLE IF NOT EXISTS wc_teacher_availability (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    teacher_user_id INTEGER NOT NULL,
    campus_id INTEGER NOT NULL,
    weekday INTEGER NOT NULL,
    start_minute INTEGER NOT NULL,
    end_minute INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS wc_contracts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    campus_id INTEGER NOT NULL,
    student_id INTEGER NOT NULL,
    course_id INTEGER NOT NULL,
    class_id INTEGER NOT NULL DEFAULT 0,
    billing_mode TEXT NOT NULL,
    list_price_cents INTEGER NOT NULL DEFAULT 0,
    discount_cents INTEGER NOT NULL DEFAULT 0,
    receivable_cents INTEGER NOT NULL DEFAULT 0,
    received_cents INTEGER NOT NULL DEFAULT 0,
    refunded_cents INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    rule_snapshot_json TEXT NOT NULL DEFAULT '{}',
    pack_paid_milli INTEGER NOT NULL DEFAULT 0,
    pack_bonus_milli INTEGER NOT NULL DEFAULT 0,
    pack_expire_at INTEGER NOT NULL DEFAULT 0,
    term_start INTEGER NOT NULL DEFAULT 0,
    term_end INTEGER NOT NULL DEFAULT 0,
    term_promised_sessions INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    created_by INTEGER NOT NULL DEFAULT 0,
    idempotency_key TEXT NOT NULL DEFAULT '',
    UNIQUE (org_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS wc_payments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    contract_id INTEGER NOT NULL,
    amount_cents INTEGER NOT NULL,
    method TEXT NOT NULL,
    receipt_no TEXT NOT NULL,
    note TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    created_by INTEGER NOT NULL DEFAULT 0,
    idempotency_key TEXT NOT NULL DEFAULT '',
    UNIQUE (org_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS wc_refunds (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    contract_id INTEGER NOT NULL,
    payment_id INTEGER NOT NULL,
    amount_cents INTEGER NOT NULL,
    reason TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    created_by INTEGER NOT NULL DEFAULT 0,
    idempotency_key TEXT NOT NULL DEFAULT '',
    UNIQUE (org_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS wc_lesson_ledger (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    contract_id INTEGER NOT NULL,
    student_id INTEGER NOT NULL,
    entry_type TEXT NOT NULL,
    bucket TEXT NOT NULL DEFAULT '',
    milli_delta INTEGER NOT NULL,
    balance_paid_after INTEGER NOT NULL DEFAULT 0,
    balance_bonus_after INTEGER NOT NULL DEFAULT 0,
    session_id INTEGER NOT NULL DEFAULT 0,
    note TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    created_by INTEGER NOT NULL DEFAULT 0,
    idempotency_key TEXT NOT NULL DEFAULT '',
    UNIQUE (org_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS wc_pack_balances (
    contract_id INTEGER PRIMARY KEY,
    paid_remaining_milli INTEGER NOT NULL DEFAULT 0,
    bonus_remaining_milli INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS wc_schedule_series (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    campus_id INTEGER NOT NULL,
    class_id INTEGER NOT NULL,
    teacher_user_id INTEGER NOT NULL,
    room_id INTEGER NOT NULL,
    weekday INTEGER NOT NULL,
    start_minute INTEGER NOT NULL,
    end_minute INTEGER NOT NULL,
    series_start INTEGER NOT NULL,
    series_end INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS wc_lesson_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    campus_id INTEGER NOT NULL,
    class_id INTEGER NOT NULL,
    series_id INTEGER NOT NULL DEFAULT 0,
    teacher_user_id INTEGER NOT NULL,
    room_id INTEGER NOT NULL,
    start_at INTEGER NOT NULL,
    end_at INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'SCHEDULED',
    cancel_reason TEXT NOT NULL DEFAULT '',
    makeup_of_session_id INTEGER NOT NULL DEFAULT 0,
    contract_id INTEGER NOT NULL DEFAULT 0,
    student_id INTEGER NOT NULL DEFAULT 0,
    original_start_at INTEGER NOT NULL DEFAULT 0,
    change_note TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS wc_session_students (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id INTEGER NOT NULL,
    student_id INTEGER NOT NULL,
    contract_id INTEGER NOT NULL,
    UNIQUE (session_id, student_id)
);

CREATE TABLE IF NOT EXISTS wc_attendance (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    session_id INTEGER NOT NULL,
    student_id INTEGER NOT NULL,
    contract_id INTEGER NOT NULL,
    status TEXT NOT NULL,
    consumed_milli INTEGER NOT NULL DEFAULT 0,
    ledger_id INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    created_by INTEGER NOT NULL DEFAULT 0,
    reversed_at INTEGER NOT NULL DEFAULT 0,
    idempotency_key TEXT NOT NULL DEFAULT '',
    UNIQUE (org_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS wc_leave_requests (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    session_id INTEGER NOT NULL,
    student_id INTEGER NOT NULL,
    contract_id INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    reason TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    created_by INTEGER NOT NULL DEFAULT 0,
    resolved_at INTEGER NOT NULL DEFAULT 0,
    makeup_session_id INTEGER NOT NULL DEFAULT 0,
    idempotency_key TEXT NOT NULL DEFAULT '',
    UNIQUE (org_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS wc_feedback (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    session_id INTEGER NOT NULL,
    student_id INTEGER NOT NULL,
    teacher_user_id INTEGER NOT NULL,
    tags TEXT NOT NULL DEFAULT '',
    body TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    idempotency_key TEXT NOT NULL DEFAULT '',
    UNIQUE (org_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS wc_works (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    student_id INTEGER NOT NULL,
    session_id INTEGER NOT NULL DEFAULT 0,
    title TEXT NOT NULL DEFAULT '',
    storage_path TEXT NOT NULL DEFAULT '',
    is_public INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS wc_notifications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL DEFAULT '',
    ref_type TEXT NOT NULL DEFAULT '',
    ref_id INTEGER NOT NULL DEFAULT 0,
    read_at INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS wc_consume_revenue (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    contract_id INTEGER NOT NULL,
    session_id INTEGER NOT NULL,
    amount_cents INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    idempotency_key TEXT NOT NULL DEFAULT '',
    UNIQUE (org_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS wc_idempotency (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    idempotency_key TEXT NOT NULL,
    result_json TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    UNIQUE (org_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS wc_audit (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    actor_user_id INTEGER NOT NULL DEFAULT 0,
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id INTEGER NOT NULL DEFAULT 0,
    detail TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_wc_sessions_teacher ON wc_lesson_sessions(teacher_user_id, start_at, end_at);
CREATE INDEX IF NOT EXISTS idx_wc_sessions_room ON wc_lesson_sessions(room_id, start_at, end_at);
CREATE INDEX IF NOT EXISTS idx_wc_attendance_session ON wc_attendance(session_id, student_id);
