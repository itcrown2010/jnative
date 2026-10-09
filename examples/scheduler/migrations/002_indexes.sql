-- Author: 张晓达
CREATE INDEX IF NOT EXISTS idx_events_calendar_start ON events(calendar_id, start_time);
CREATE INDEX IF NOT EXISTS idx_events_range ON events(start_time, end_time);
CREATE INDEX IF NOT EXISTS idx_tasks_user_due ON tasks(user_id, due_at);
CREATE INDEX IF NOT EXISTS idx_sessions_token ON sessions(token_hash);
CREATE INDEX IF NOT EXISTS idx_calendars_owner ON calendars(owner_id);
CREATE INDEX IF NOT EXISTS idx_reminders_event ON reminders(event_id);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
