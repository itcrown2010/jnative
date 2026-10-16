/**
 * Wonder Campus enums / constants. Money = cents. Lessons = lesson_milli (1000 = 1课时).
 * @author 张晓达
 */
class WcIdentity {
    static String ADMIN = "ADMIN";
    static String TEACHER = "TEACHER";
    static String PARENT = "PARENT";
    /** 运营负责人：可看可追溯经营概览；不默认拥有全部校区敏感字段 */
    static String DIRECTOR = "DIRECTOR";
}

class WcBillingMode {
    static String PACK = "PACK";
    static String TERM = "TERM";
}

class WcSessionStatus {
    static String SCHEDULED = "SCHEDULED";
    static String COMPLETED = "COMPLETED";
    static String CANCELLED_BY_SCHOOL = "CANCELLED_BY_SCHOOL";
    static String RESCHEDULED = "RESCHEDULED";
}

class WcAttendanceStatus {
    static String NOT_RECORDED = "NOT_RECORDED";
    static String PRESENT = "PRESENT";
    static String LATE = "LATE";
    static String ABSENT = "ABSENT";
    static String LEAVE = "LEAVE";
    static String REVERSED = "REVERSED";
}

class WcFeedbackStatus {
    static String DRAFT = "DRAFT";
    static String PUBLISHED = "PUBLISHED";
}

class WcMakeupStatus {
    static String OPEN = "OPEN";
    static String USED = "USED";
    static String EXPIRED = "EXPIRED";
    static String CANCELLED = "CANCELLED";
}

class WcNotifyTaskStatus {
    static String PENDING = "PENDING";
    static String DONE = "DONE";
    static String FAILED = "FAILED";
}

class WcLedgerType {
    static String PURCHASE = "PURCHASE";
    static String CONSUME = "CONSUME";
    static String REVERSE_CONSUME = "REVERSE_CONSUME";
    static String REFUND = "REFUND";
    static String EXPIRE = "EXPIRE";
    static String TERM_GRANT = "TERM_GRANT";
}

class WcBucket {
    static String PAID = "PAID";
    static String BONUS = "BONUS";
    static String TERM = "TERM";
}

class WcPayMethod {
    static String WECHAT = "WECHAT";
    static String ALIPAY = "ALIPAY";
    static String BANK = "BANK";
    static String CASH = "CASH";
}

class WcAgeBand {
    static String CHILD = "CHILD";
    static String TEEN = "TEEN";
    static String HIGH = "HIGH";
}

class WcLeaveStatus {
    static String PENDING = "PENDING";
    static String APPROVED = "APPROVED";
    static String REJECTED = "REJECTED";
}
