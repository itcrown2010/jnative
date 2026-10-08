-- Enterprise Foundation 1.4 (PostgreSQL). Controlled metadata — no arbitrary SQL / eval.

CREATE TABLE IF NOT EXISTS schema_migrations (
    version TEXT PRIMARY KEY,
    applied_at BIGINT NOT NULL,
    checksum TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS platform_permissions (
    id BIGSERIAL PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS platform_roles (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    data_scope TEXT NOT NULL DEFAULT 'COMPANY',
    UNIQUE (company_id, code)
);

CREATE TABLE IF NOT EXISTS platform_role_permissions (
    role_id BIGINT NOT NULL REFERENCES platform_roles(id),
    permission_id BIGINT NOT NULL REFERENCES platform_permissions(id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS platform_user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL REFERENCES platform_roles(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS custom_field_definitions (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    entity_type TEXT NOT NULL,
    field_code TEXT NOT NULL,
    field_name TEXT NOT NULL,
    field_type TEXT NOT NULL,
    required INT NOT NULL DEFAULT 0,
    default_value TEXT NOT NULL DEFAULT '',
    validation_rules TEXT NOT NULL DEFAULT '',
    active INT NOT NULL DEFAULT 1,
    UNIQUE (company_id, entity_type, field_code)
);

CREATE TABLE IF NOT EXISTS custom_field_values (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id BIGINT NOT NULL,
    field_code TEXT NOT NULL,
    value_text TEXT NOT NULL DEFAULT '',
    UNIQUE (company_id, entity_type, entity_id, field_code)
);

CREATE TABLE IF NOT EXISTS form_definitions (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    form_code TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    title TEXT NOT NULL,
    active INT NOT NULL DEFAULT 1,
    UNIQUE (company_id, form_code)
);

CREATE TABLE IF NOT EXISTS form_field_definitions (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    form_id BIGINT NOT NULL REFERENCES form_definitions(id),
    field_code TEXT NOT NULL,
    field_name TEXT NOT NULL,
    field_type TEXT NOT NULL,
    visible INT NOT NULL DEFAULT 1,
    readonly INT NOT NULL DEFAULT 0,
    required INT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 10,
    field_group TEXT NOT NULL DEFAULT 'main'
);

CREATE TABLE IF NOT EXISTS workflow_definitions (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    workflow_code TEXT NOT NULL,
    name TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    active INT NOT NULL DEFAULT 1,
    UNIQUE (company_id, workflow_code)
);

CREATE TABLE IF NOT EXISTS workflow_versions (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    workflow_id BIGINT NOT NULL REFERENCES workflow_definitions(id),
    version_no INT NOT NULL,
    definition_json TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE (workflow_id, version_no)
);

CREATE TABLE IF NOT EXISTS workflow_instances (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    workflow_version_id BIGINT NOT NULL REFERENCES workflow_versions(id),
    entity_type TEXT NOT NULL,
    entity_id BIGINT NOT NULL,
    status TEXT NOT NULL,
    current_step INT NOT NULL DEFAULT 1,
    amount_snapshot BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS workflow_tasks (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL REFERENCES workflow_instances(id),
    step_no INT NOT NULL,
    assignee_role TEXT NOT NULL DEFAULT '',
    assignee_user_id BIGINT NOT NULL DEFAULT 0,
    status TEXT NOT NULL,
    action TEXT NOT NULL DEFAULT '',
    comment_text TEXT NOT NULL DEFAULT '',
    acted_by BIGINT NOT NULL DEFAULT 0,
    acted_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS report_definitions (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    report_code TEXT NOT NULL,
    name TEXT NOT NULL,
    dataset_code TEXT NOT NULL,
    active INT NOT NULL DEFAULT 1,
    UNIQUE (company_id, report_code)
);

CREATE TABLE IF NOT EXISTS report_executions (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    report_id BIGINT NOT NULL,
    executed_by BIGINT NOT NULL,
    row_count INT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS import_jobs (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    job_code TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    status TEXT NOT NULL,
    total_rows INT NOT NULL DEFAULT 0,
    ok_rows INT NOT NULL DEFAULT 0,
    error_rows INT NOT NULL DEFAULT 0,
    error_report TEXT NOT NULL DEFAULT '',
    created_by BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE (company_id, job_code)
);

CREATE TABLE IF NOT EXISTS system_settings (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 0,
    setting_key TEXT NOT NULL,
    setting_type TEXT NOT NULL,
    setting_value TEXT NOT NULL,
    sensitive INT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL,
    UNIQUE (company_id, setting_key)
);

CREATE TABLE IF NOT EXISTS setting_history (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 0,
    setting_key TEXT NOT NULL,
    old_value TEXT NOT NULL DEFAULT '',
    new_value TEXT NOT NULL DEFAULT '',
    changed_by BIGINT NOT NULL,
    changed_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS audit_events (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    resource_type TEXT NOT NULL,
    resource_id BIGINT NOT NULL DEFAULT 0,
    action TEXT NOT NULL,
    before_json TEXT NOT NULL DEFAULT '',
    after_json TEXT NOT NULL DEFAULT '',
    request_id TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS departments (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    UNIQUE (company_id, code)
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS department_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS owner_user_id BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_cf_values ON custom_field_values(company_id, entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_wf_inst ON workflow_instances(company_id, entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_events ON audit_events(company_id, created_at);
