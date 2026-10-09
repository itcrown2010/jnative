/**
 * @author 张晓达
 */
class UserDto {
    long id;
    String email;
    String displayName;
    String timezone;

    UserDto() {
        id = 0;
        email = "";
        displayName = "";
        timezone = "Asia/Shanghai";
    }
}

class AuthSessionDto {
    UserDto user;
    String csrfToken;

    AuthSessionDto() {
        user = new UserDto();
        csrfToken = "";
    }
}

class LoginRequest {
    String email;
    String password;

    LoginRequest() {
        email = "";
        password = "";
    }
}

class CalendarDto {
    long id;
    String name;
    String description;
    String color;
    int isDefault;

    CalendarDto() {
        id = 0;
        name = "";
        description = "";
        color = "#3B82F6";
        isDefault = 0;
    }
}

class EventDto {
    long id;
    long calendarId;
    String title;
    String description;
    String location;
    long startTime;
    long endTime;
    int allDay;
    String timezone;
    String recurrenceRule;
    String status;
    String color;
    int minutesBefore;

    EventDto() {
        id = 0;
        calendarId = 0;
        title = "";
        description = "";
        location = "";
        startTime = 0;
        endTime = 0;
        allDay = 0;
        timezone = "UTC";
        recurrenceRule = "";
        status = EventStatus.CONFIRMED;
        color = "#3B82F6";
        minutesBefore = 10;
    }
}

class CreateEventRequest {
    long calendarId;
    String title;
    String description;
    String location;
    long startTime;
    long endTime;
    int allDay;
    String timezone;
    String recurrenceRule;
    int minutesBefore;

    CreateEventRequest() {
        calendarId = 0;
        title = "";
        description = "";
        location = "";
        startTime = 0;
        endTime = 0;
        allDay = 0;
        timezone = "Asia/Shanghai";
        recurrenceRule = "";
        minutesBefore = 10;
    }
}

class UpdateEventRequest {
    long calendarId;
    String title;
    String description;
    String location;
    long startTime;
    long endTime;
    int allDay;
    String timezone;
    String recurrenceRule;
    String status;
    int minutesBefore;

    UpdateEventRequest() {
        calendarId = 0;
        title = "";
        description = "";
        location = "";
        startTime = 0;
        endTime = 0;
        allDay = 0;
        timezone = "Asia/Shanghai";
        recurrenceRule = "";
        status = EventStatus.CONFIRMED;
        minutesBefore = 10;
    }
}

class TaskDto {
    long id;
    String title;
    String description;
    long dueAt;
    String priority;
    String status;
    long createdAt;
    long completedAt;

    TaskDto() {
        id = 0;
        title = "";
        description = "";
        dueAt = 0;
        priority = TaskPriority.NORMAL;
        status = TaskStatus.OPEN;
        createdAt = 0;
        completedAt = 0;
    }
}

class CreateTaskRequest {
    String title;
    String description;
    long dueAt;
    String priority;

    CreateTaskRequest() {
        title = "";
        description = "";
        dueAt = 0;
        priority = TaskPriority.NORMAL;
    }
}

class UpdateTaskRequest {
    String title;
    String description;
    long dueAt;
    String priority;
    String status;

    UpdateTaskRequest() {
        title = "";
        description = "";
        dueAt = 0;
        priority = TaskPriority.NORMAL;
        status = TaskStatus.OPEN;
    }
}

class SearchResultDto {
    String kind;
    long id;
    String title;
    String snippet;
    long whenMs;

    SearchResultDto() {
        kind = "";
        id = 0;
        title = "";
        snippet = "";
        whenMs = 0;
    }
}

class ApiError {
    String code;
    String message;
    String requestId;

    ApiError() {
        code = "INTERNAL_ERROR";
        message = "";
        requestId = "";
    }

    ApiError(String code, String message, String requestId) {
        this.code = code;
        this.message = message;
        this.requestId = requestId;
    }
}
