-- Author: 张晓达
-- guide-tasks sync schema (SQLite). Real DB — not an in-memory mock.
-- schema_version 2: multi-user Token sessions (local-dev).

PRAGMA journal_mode=WAL;
PRAGMA foreign_keys=ON;

CREATE TABLE IF NOT EXISTS meta (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  username TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions (
  token TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  expires_at INTEGER NOT NULL,
  revoked INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS tasks (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL DEFAULT '',
  version INTEGER NOT NULL,
  title TEXT NOT NULL,
  notes TEXT NOT NULL DEFAULT '',
  status INTEGER NOT NULL DEFAULT 0,
  due_ymd INTEGER NOT NULL DEFAULT 0,
  deleted INTEGER NOT NULL DEFAULT 0,
  updated_seq INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS idempotency (
  op_id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL DEFAULT '',
  task_id TEXT NOT NULL,
  status_code INTEGER NOT NULL,
  response_json TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_tasks_seq ON tasks(updated_seq);
CREATE INDEX IF NOT EXISTS idx_tasks_user_seq ON tasks(user_id, updated_seq);
CREATE INDEX IF NOT EXISTS idx_tasks_deleted ON tasks(deleted);
CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);

INSERT OR IGNORE INTO meta(key, value) VALUES ('schema_version', '2');
INSERT OR IGNORE INTO meta(key, value) VALUES ('change_seq', '0');
