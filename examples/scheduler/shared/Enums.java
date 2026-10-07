/** Shared enums as string constants (JNative has limited enum support). */
class EventStatus {
    static String CONFIRMED = "CONFIRMED";
    static String TENTATIVE = "TENTATIVE";
    static String CANCELLED = "CANCELLED";
}

class TaskStatus {
    static String OPEN = "OPEN";
    static String DONE = "DONE";
    static String CANCELLED = "CANCELLED";
}

class TaskPriority {
    static String LOW = "LOW";
    static String NORMAL = "NORMAL";
    static String HIGH = "HIGH";
}

class ReminderType {
    static String IN_APP = "IN_APP";
    static String EMAIL = "EMAIL";
    static String PUSH = "PUSH";
}

class CalendarView {
    static String MONTH = "MONTH";
    static String WEEK = "WEEK";
    static String DAY = "DAY";
}

class RecurrenceFreq {
    static String NONE = "";
    static String DAILY = "DAILY";
    static String WEEKLY = "WEEKLY";
    static String MONTHLY = "MONTHLY";
}
