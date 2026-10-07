class EventValidation {
    static String validateCreate(CreateEventRequest req) {
        if (req == null) {
            return "request required";
        }
        if (req.title == null || req.title.length() == 0) {
            return "title not blank";
        }
        if (req.title.length() > 200) {
            return "title too long";
        }
        if (req.startTime <= 0) {
            return "startTime required";
        }
        if (req.endTime < req.startTime) {
            return "endTime >= startTime";
        }
        if (req.calendarId <= 0) {
            return "calendarId required";
        }
        return "";
    }

    static String validateUpdate(UpdateEventRequest req) {
        if (req == null) {
            return "request required";
        }
        if (req.title != null && req.title.length() > 200) {
            return "title too long";
        }
        if (req.startTime > 0 && req.endTime > 0 && req.endTime < req.startTime) {
            return "endTime >= startTime";
        }
        return "";
    }
}

class TaskValidation {
    static String validateCreate(CreateTaskRequest req) {
        if (req == null || req.title == null || req.title.length() == 0) {
            return "title not blank";
        }
        if (req.title.length() > 200) {
            return "title too long";
        }
        return "";
    }
}

class LoginValidation {
    static String validate(LoginRequest req) {
        if (req == null) {
            return "request required";
        }
        if (req.email == null || req.email.length() == 0) {
            return "email required";
        }
        if (req.password == null || req.password.length() == 0) {
            return "password required";
        }
        return "";
    }
}
