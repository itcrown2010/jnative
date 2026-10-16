-- Author: 张晓达
-- Wonder Campus commercial modules: invites, products, leads, announcements, homework, reviews, payroll, settings.

CREATE TABLE IF NOT EXISTS wc_invites (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    code TEXT NOT NULL UNIQUE,
    identity_kind TEXT NOT NULL,
    student_id INTEGER NOT NULL DEFAULT 0,
    campus_id INTEGER NOT NULL DEFAULT 0,
    note TEXT NOT NULL DEFAULT '',
    created_by INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    used_by INTEGER NOT NULL DEFAULT 0,
    used_at INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS wc_products (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    course_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    kind TEXT NOT NULL,
    paid_milli INTEGER NOT NULL DEFAULT 0,
    bonus_milli INTEGER NOT NULL DEFAULT 0,
    valid_days INTEGER NOT NULL DEFAULT 180,
    term_sessions INTEGER NOT NULL DEFAULT 0,
    term_days INTEGER NOT NULL DEFAULT 90,
    price_cents INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS wc_leads (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    campus_id INTEGER NOT NULL DEFAULT 0,
    child_name TEXT NOT NULL,
    child_age INTEGER NOT NULL DEFAULT 0,
    guardian_name TEXT NOT NULL DEFAULT '',
    phone TEXT NOT NULL DEFAULT '',
    source TEXT NOT NULL DEFAULT 'WALK_IN',
    intent_course_id INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'NEW',
    owner_user_id INTEGER NOT NULL DEFAULT 0,
    next_follow_at INTEGER NOT NULL DEFAULT 0,
    lost_reason TEXT NOT NULL DEFAULT '',
    converted_student_id INTEGER NOT NULL DEFAULT 0,
    note TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS wc_lead_follows (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    lead_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    kind TEXT NOT NULL DEFAULT 'NOTE',
    content TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS wc_trials (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    lead_id INTEGER NOT NULL,
    campus_id INTEGER NOT NULL DEFAULT 0,
    course_id INTEGER NOT NULL DEFAULT 0,
    teacher_user_id INTEGER NOT NULL DEFAULT 0,
    start_at INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'BOOKED',
    result_note TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS wc_announcements (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    campus_id INTEGER NOT NULL DEFAULT 0,
    audience TEXT NOT NULL DEFAULT 'ALL',
    title TEXT NOT NULL,
    body TEXT NOT NULL DEFAULT '',
    pinned INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'PUBLISHED',
    created_by INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS wc_announcement_reads (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    announcement_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    read_at INTEGER NOT NULL,
    UNIQUE (announcement_id, user_id)
);

CREATE TABLE IF NOT EXISTS wc_homework (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    class_id INTEGER NOT NULL DEFAULT 0,
    session_id INTEGER NOT NULL DEFAULT 0,
    student_id INTEGER NOT NULL DEFAULT 0,
    title TEXT NOT NULL,
    body TEXT NOT NULL DEFAULT '',
    due_at INTEGER NOT NULL DEFAULT 0,
    created_by INTEGER NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS wc_homework_submissions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    homework_id INTEGER NOT NULL,
    student_id INTEGER NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    media_id INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'SUBMITTED',
    stars INTEGER NOT NULL DEFAULT 0,
    comment TEXT NOT NULL DEFAULT '',
    reviewed_by INTEGER NOT NULL DEFAULT 0,
    reviewed_at INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    UNIQUE (homework_id, student_id)
);

CREATE TABLE IF NOT EXISTS wc_reviews (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    session_id INTEGER NOT NULL,
    student_id INTEGER NOT NULL,
    teacher_user_id INTEGER NOT NULL,
    parent_user_id INTEGER NOT NULL,
    rating INTEGER NOT NULL,
    comment TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    UNIQUE (session_id, student_id)
);

CREATE TABLE IF NOT EXISTS wc_teacher_rates (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    teacher_user_id INTEGER NOT NULL UNIQUE,
    per_session_cents INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS wc_settings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    org_id INTEGER NOT NULL,
    skey TEXT NOT NULL,
    svalue TEXT NOT NULL DEFAULT '',
    UNIQUE (org_id, skey)
);

CREATE TABLE IF NOT EXISTS wc_class_students (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    class_id INTEGER NOT NULL,
    student_id INTEGER NOT NULL,
    joined_at INTEGER NOT NULL,
    UNIQUE (class_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_wc_leads_org ON wc_leads(org_id, status);
CREATE INDEX IF NOT EXISTS idx_wc_ann_org ON wc_announcements(org_id, created_at);
CREATE INDEX IF NOT EXISTS idx_wc_hw_class ON wc_homework(org_id, class_id);
