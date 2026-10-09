import jnative.json.*;

/**
 * @author 张晓达
 */
class SchJson {
    static String esc(String s) {
        if (s == null) {
            return "";
        }
        String out = "";
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c == 34) {
                out = out + "\\\"";
            } else if (c == 92) {
                out = out + "\\\\";
            } else if (c == 10) {
                out = out + "\\n";
            } else {
                out = out + s.substring(i, i + 1);
            }
            i = i + 1;
        }
        return out;
    }

    static String user(UserDto u) {
        return "{\"id\":" + DbParseUtil.longToString(u.id) + ",\"email\":\"" + esc(u.email) + "\",\"displayName\":\"" + esc(u.displayName)
                + "\",\"timezone\":\"" + esc(u.timezone) + "\"}";
    }

    static String authSession(AuthSessionDto a) {
        return "{\"user\":" + user(a.user) + ",\"csrfToken\":\"" + esc(a.csrfToken) + "\"}";
    }

    static String calendar(CalendarDto c) {
        return "{\"id\":" + DbParseUtil.longToString(c.id) + ",\"name\":\"" + esc(c.name) + "\",\"description\":\"" + esc(c.description)
                + "\",\"color\":\"" + esc(c.color) + "\",\"isDefault\":" + String.valueOf(c.isDefault) + "}";
    }

    static String calendars(ArrayList<Object> rows) {
        String out = "[";
        int i = 0;
        while (i < rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + calendar((CalendarDto) rows.get(i));
            i = i + 1;
        }
        return out + "]";
    }

    static String event(EventDto e) {
        return "{\"id\":" + DbParseUtil.longToString(e.id) + ",\"calendarId\":" + DbParseUtil.longToString(e.calendarId) + ",\"title\":\"" + esc(e.title)
                + "\",\"description\":\"" + esc(e.description) + "\",\"location\":\"" + esc(e.location)
                + "\",\"startTime\":" + DbParseUtil.longToString(e.startTime) + ",\"endTime\":" + DbParseUtil.longToString(e.endTime) + ",\"allDay\":" + String.valueOf(e.allDay)
                + ",\"timezone\":\"" + esc(e.timezone) + "\",\"recurrenceRule\":\"" + esc(e.recurrenceRule)
                + "\",\"status\":\"" + esc(e.status) + "\",\"color\":\"" + esc(e.color)
                + "\",\"minutesBefore\":" + String.valueOf(e.minutesBefore) + "}";
    }

    static String events(ArrayList<Object> rows) {
        String out = "[";
        int i = 0;
        while (i < rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + event((EventDto) rows.get(i));
            i = i + 1;
        }
        return out + "]";
    }

    static String task(TaskDto t) {
        return "{\"id\":" + DbParseUtil.longToString(t.id) + ",\"title\":\"" + esc(t.title) + "\",\"description\":\"" + esc(t.description)
                + "\",\"dueAt\":" + DbParseUtil.longToString(t.dueAt) + ",\"priority\":\"" + esc(t.priority) + "\",\"status\":\"" + esc(t.status)
                + "\",\"createdAt\":" + DbParseUtil.longToString(t.createdAt) + ",\"completedAt\":" + DbParseUtil.longToString(t.completedAt) + "}";
    }

    static String tasks(ArrayList<Object> rows) {
        String out = "[";
        int i = 0;
        while (i < rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            out = out + task((TaskDto) rows.get(i));
            i = i + 1;
        }
        return out + "]";
    }

    static String searchResults(ArrayList<Object> rows) {
        String out = "[";
        int i = 0;
        while (i < rows.size()) {
            if (i > 0) {
                out = out + ",";
            }
            SearchResultDto s = (SearchResultDto) rows.get(i);
            out = out + "{\"kind\":\"" + esc(s.kind) + "\",\"id\":" + DbParseUtil.longToString(s.id) + ",\"title\":\"" + esc(s.title)
                    + "\",\"snippet\":\"" + esc(s.snippet) + "\",\"whenMs\":" + DbParseUtil.longToString(s.whenMs) + "}";
            i = i + 1;
        }
        return out + "]";
    }

    static CreateEventRequest parseCreateEvent(String body) {
        JsonObject o = Json.parse(body).asObject();
        CreateEventRequest r = new CreateEventRequest();
        r.calendarId = o.getLong("calendarId", 0);
        r.title = o.getString("title", "");
        r.description = o.getString("description", "");
        r.location = o.getString("location", "");
        r.startTime = o.getLong("startTime", 0);
        r.endTime = o.getLong("endTime", 0);
        r.allDay = o.getInt("allDay", 0);
        r.timezone = o.getString("timezone", "Asia/Shanghai");
        r.recurrenceRule = o.getString("recurrenceRule", "");
        r.minutesBefore = o.getInt("minutesBefore", 10);
        return r;
    }

    static UpdateEventRequest parseUpdateEvent(JsonObject o) {
        UpdateEventRequest r = new UpdateEventRequest();
        r.calendarId = o.getLong("calendarId", 0);
        r.title = o.getString("title", "");
        r.description = o.getString("description", "");
        r.location = o.getString("location", "");
        r.startTime = o.getLong("startTime", 0);
        r.endTime = o.getLong("endTime", 0);
        r.allDay = o.getInt("allDay", 0);
        r.timezone = o.getString("timezone", "Asia/Shanghai");
        r.recurrenceRule = o.getString("recurrenceRule", "");
        r.status = o.getString("status", EventStatus.CONFIRMED);
        r.minutesBefore = o.getInt("minutesBefore", 10);
        return r;
    }

    static CreateTaskRequest parseCreateTask(String body) {
        JsonObject o = Json.parse(body).asObject();
        CreateTaskRequest r = new CreateTaskRequest();
        r.title = o.getString("title", "");
        r.description = o.getString("description", "");
        r.dueAt = o.getLong("dueAt", 0);
        r.priority = o.getString("priority", TaskPriority.NORMAL);
        return r;
    }

    static UpdateTaskRequest parseUpdateTask(JsonObject o) {
        UpdateTaskRequest r = new UpdateTaskRequest();
        r.title = o.getString("title", "");
        r.description = o.getString("description", "");
        r.dueAt = o.getLong("dueAt", 0);
        r.priority = o.getString("priority", TaskPriority.NORMAL);
        r.status = o.getString("status", TaskStatus.OPEN);
        return r;
    }
}

class SchNum {
    static long parseLong(String s) {
        if (s == null || s.length() == 0) {
            return 0;
        }
        return DbParseUtil.parseLong(s);
    }
}
