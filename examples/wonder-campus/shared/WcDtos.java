/**
 * Wonder Campus result / transfer objects.
 * @author 张晓达
 */
class WcResult {
    int ok;
    String code;
    String message;
    String documentJson;

    static WcResult fail(String code, String message) {
        WcResult r = new WcResult();
        r.ok = 0;
        r.code = code;
        r.message = message;
        r.documentJson = "";
        return r;
    }

    static WcResult success(String code, String message, String documentJson) {
        WcResult r = new WcResult();
        r.ok = 1;
        r.code = code;
        r.message = message;
        r.documentJson = documentJson;
        return r;
    }
}

class WcRuleSnapshot {
    int leaveDeadlineHours;
    int leaveConsumes;
    int absentConsumes;
    int allowMakeup;
    int makeupValidDays;
    int allowCrossCampusMakeup;
    String deductOrder;
    long sessionConsumeMilli;

    static WcRuleSnapshot defaults() {
        WcRuleSnapshot s = new WcRuleSnapshot();
        s.leaveDeadlineHours = 12;
        s.leaveConsumes = 0;
        s.absentConsumes = 0;
        s.allowMakeup = 1;
        s.makeupValidDays = 30;
        s.allowCrossCampusMakeup = 0;
        s.deductOrder = "PAID_FIRST";
        s.sessionConsumeMilli = 1000L;
        return s;
    }

    String toJson() {
        return "{\"leaveDeadlineHours\":" + CryptoParse.longToString(leaveDeadlineHours)
                + ",\"leaveConsumes\":" + CryptoParse.longToString(leaveConsumes)
                + ",\"absentConsumes\":" + CryptoParse.longToString(absentConsumes)
                + ",\"allowMakeup\":" + CryptoParse.longToString(allowMakeup)
                + ",\"makeupValidDays\":" + CryptoParse.longToString(makeupValidDays)
                + ",\"allowCrossCampusMakeup\":" + CryptoParse.longToString(allowCrossCampusMakeup)
                + ",\"deductOrder\":\"" + deductOrder + "\""
                + ",\"sessionConsumeMilli\":" + CryptoParse.longToString(sessionConsumeMilli) + "}";
    }
}
