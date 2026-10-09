/**
 * Recurrence engine — stores base event + RRULE-like frequency; expands in range.
 * Does not materialize all future occurrences into the database.
 * @author 张晓达
 */
class RecurrenceRule {
    String freq;
    int interval;

    RecurrenceRule() {
        freq = RecurrenceFreq.NONE;
        interval = 1;
    }

    static RecurrenceRule parse(String s) {
        RecurrenceRule r = new RecurrenceRule();
        if (s == null || s.length() == 0) {
            return r;
        }
        if (s.equals("DAILY") || s.indexOf("FREQ=DAILY") >= 0) {
            r.freq = RecurrenceFreq.DAILY;
        } else if (s.equals("WEEKLY") || s.indexOf("FREQ=WEEKLY") >= 0) {
            r.freq = RecurrenceFreq.WEEKLY;
        } else if (s.equals("MONTHLY") || s.indexOf("FREQ=MONTHLY") >= 0) {
            r.freq = RecurrenceFreq.MONTHLY;
        }
        return r;
    }

    String toRuleString() {
        return freq;
    }
}

class RecurrenceIterator {
    long startMs;
    long endMs;
    long durationMs;
    RecurrenceRule rule;
    long cursor;
    long rangeEnd;
    int emitted;

    RecurrenceIterator(long startMs, long endMs, RecurrenceRule rule, long rangeStart, long rangeEnd) {
        this.startMs = startMs;
        this.endMs = endMs;
        this.durationMs = endMs - startMs;
        if (this.durationMs < 0) {
            this.durationMs = 0;
        }
        this.rule = rule;
        this.cursor = startMs;
        this.rangeEnd = rangeEnd;
        this.emitted = 0;
        while (this.cursor + this.durationMs < rangeStart && this.emitted < 10000) {
            advance();
            this.emitted = this.emitted + 1;
        }
        this.emitted = 0;
    }

    int hasNext() {
        if (emitted >= 366) {
            return 0;
        }
        if (cursor > rangeEnd) {
            return 0;
        }
        return 1;
    }

    long nextStart() {
        long s = cursor;
        advance();
        emitted = emitted + 1;
        return s;
    }

    void advance() {
        if (rule.freq.equals(RecurrenceFreq.DAILY)) {
            cursor = cursor + 86400000L * rule.interval * 1L;
        } else if (rule.freq.equals(RecurrenceFreq.WEEKLY)) {
            cursor = cursor + 7L * 86400000L * rule.interval * 1L;
        } else if (rule.freq.equals(RecurrenceFreq.MONTHLY)) {
            cursor = cursor + 30L * 86400000L * rule.interval * 1L;
        } else {
            cursor = rangeEnd + 1;
        }
    }

    static ArrayList<Object> expand(EventDto base, long fromMs, long toMs) {
        ArrayList<Object> out = new ArrayList<Object>();
        RecurrenceRule rule = RecurrenceRule.parse(base.recurrenceRule);
        if (rule.freq.equals(RecurrenceFreq.NONE)) {
            if (base.endTime >= fromMs && base.startTime <= toMs) {
                out.add(base);
            }
            return out;
        }
        RecurrenceIterator it = new RecurrenceIterator(base.startTime, base.endTime, rule, fromMs, toMs);
        while (it.hasNext() != 0) {
            long s = it.nextStart();
            long e = s + it.durationMs;
            if (e >= fromMs && s <= toMs) {
                EventDto o = new EventDto();
                o.id = base.id;
                o.calendarId = base.calendarId;
                o.title = base.title;
                o.description = base.description;
                o.location = base.location;
                o.startTime = s;
                o.endTime = e;
                o.allDay = base.allDay;
                o.timezone = base.timezone;
                o.recurrenceRule = base.recurrenceRule;
                o.status = base.status;
                o.color = base.color;
                o.minutesBefore = base.minutesBefore;
                out.add(o);
            }
        }
        return out;
    }
}
