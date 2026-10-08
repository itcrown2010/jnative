-- Enterprise Foundation 1.4 (SQLite mirror).

CREATE TABLE IF NOT EXISTS schema_migrations (
    version TEXT PRIMARY KEY,
    applied_at INTEGER NOT NULL,
    checksum TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS platform_permissions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS platform_roles (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    data_scope TEXT NOT NULL DEFAULT 'COMPANY',
    UNIQUE (company_id, code)
);

CREATE TABLE IF NOT EXISTS platform_role_permissions (
    role_id INTEGER NOT NULL,
    permission_id INTEGER NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS platform_user_roles (
    user_id INTEGER NOT NULL,
    role_id INTEGER NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS custom_field_definitions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    entity_type TEXT NOT NULL,
    field_code TEXT NOT NULL,
    field_name TEXT NOT NULL,
    field_type TEXT NOT NULL,
    required INTEGER NOT NULL DEFAULT 0,
    default_value TEXT NOT NULL DEFAULT '',
    validation_rules TEXT NOT NULL DEFAULT '',
    active INTEGER NOT NULL DEFAULT 1,
    UNIQUE (company_id, entity_type, field_code)
);

CREATE TABLE IF NOT EXISTS custom_field_values (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id INTEGER NOT NULL,
    field_code TEXT NOT NULL,
    value_text TEXT NOT NULL DEFAULT '',
    UNIQUE (company_id, entity_type, entity_id, field_code)
);

CREATE TABLE IF NOT EXISTS form_definitions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    form_code TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    title TEXT NOT NULL,
    active INTEGER NOT NULL DEFAULT 1,
    UNIQUE (company_id, form_code)
);

CREATE TABLE IF NOT EXISTS form_field_definitions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    form_id INTEGER NOT NULL,
    field_code TEXT NOT NULL,
    field_name TEXT NOT NULL,
    field_type TEXT NOT NULL,
    visible INTEGER NOT NULL DEFAULT 1,
    readonly INTEGER NOT NULL DEFAULT 0,
    required INTEGER NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL DEFAULT 10,
    field_group TEXT NOT NULL DEFAULT 'main'
);

CREATE TABLE IF NOT EXISTS workflow_definitions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    workflow_code TEXT NOT NULL,
    name TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    active INTEGER NOT NULL DEFAULT 1,
    UNIQUE (company_id, workflow_code)
);

CREATE TABLE IF NOT EXISTS workflow_versions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    workflow_id INTEGER NOT NULL,
    version_no INTEGER NOT NULL,
    definition_json TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    UNIQUE (workflow_id, version_no)
);

CREATE TABLE IF NOT EXISTS workflow_instances (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    workflow_version_id INTEGER NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id INTEGER NOT NULL,
    status TEXT NOT NULL,
    current_step INTEGER NOT NULL DEFAULT 1,
    amount_snapshot INTEGER NOT NULL DEFAULT 0,
    created_by INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS workflow_tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    instance_id INTEGER NOT NULL,
    step_no INTEGER NOT NULL,
    assignee_role TEXT NOT NULL DEFAULT '',
    assignee_user_id INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL,
    action TEXT NOT NULL DEFAULT '',
    comment_text TEXT NOT NULL DEFAULT '',
    acted_by INTEGER NOT NULL DEFAULT 0,
    acted_at INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS report_definitions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    report_code TEXT NOT NULL,
    name TEXT NOT NULL,
    dataset_code TEXT NOT NULL,
    active INTEGER NOT NULL DEFAULT 1,
    UNIQUE (company_id, report_code)
);

CREATE TABLE IF NOT EXISTS report_executions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    report_id INTEGER NOT NULL,
    executed_by INTEGER NOT NULL,
    row_count INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS import_jobs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    job_code TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    status TEXT NOT NULL,
    total_rows INTEGER NOT NULL DEFAULT 0,
    ok_rows INTEGER NOT NULL DEFAULT 0,
    error_rows INTEGER NOT NULL DEFAULT 0,
    error_report TEXT NOT NULL DEFAULT '',
    created_by INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    UNIQUE (company_id, job_code)
);

CREATE TABLE IF NOT EXISTS system_settings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL DEFAULT 0,
    setting_key TEXT NOT NULL,
    setting_type TEXT NOT NULL,
    setting_value TEXT NOT NULL,
    sensitive INTEGER NOT NULL DEFAULT 0,
    updated_by INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL,
    UNIQUE (company_id, setting_key)
);

CREATE TABLE IF NOT EXISTS setting_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL DEFAULT 0,
    setting_key TEXT NOT NULL,
    old_value TEXT NOT NULL DEFAULT '',
    new_value TEXT NOT NULL DEFAULT '',
    changed_by INTEGER NOT NULL,
    changed_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS audit_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    actor_user_id INTEGER NOT NULL,
    resource_type TEXT NOT NULL,
    resource_id INTEGER NOT NULL DEFAULT 0,
    action TEXT NOT NULL,
    before_json TEXT NOT NULL DEFAULT '',
    after_json TEXT NOT NULL DEFAULT '',
    request_id TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS departments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    UNIQUE (company_id, code)
);
