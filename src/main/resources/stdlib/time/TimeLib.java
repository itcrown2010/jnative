// JNative time subset (0.24) — database/Web-facing types only. Not a full java.time clone.


class TimeFmt {
    static String longToString(long v) {
        if (v == 0L) {
            return "0";
        }
        String out = "";
        int neg = 0;
        if (v < 0L) {
            neg = 1;
            v = -v;
        }
        while (v > 0L) {
            long rem = v - (v / 10L) * 10L;
            int digit = 0;
            int i = 0;
            while (i < rem) {
                digit = digit + 1;
                i = i + 1;
            }
            out = String.valueOf(digit) + out;
            v = v / 10L;
        }
        if (neg != 0) {
            out = "-" + out;
        }
        return out;
    }

    static int longToInt(long v) {
        int out = 0;
        if (v < 0L) {
            v = -v;
        }
        while (v > 0L && out < 2147483647) {
            out = out + 1;
            v = v - 1L;
        }
        return out;
    }
}

class Instant {
    long epochMillis;

    Instant(long epochMillis) {
        this.epochMillis = epochMillis;
    }

    /** Monotonic-ish clock used by async timers — do NOT use for calendar display. */
    static Instant now() {
        return new Instant(AsyncClockNative.monoMillis());
    }

    /** Wall-clock UTC epoch millis (CLOCK_REALTIME). Use for events / scheduling. */
    static Instant wallNow() {
        return new Instant(AsyncClockNative.wallMillis());
    }

    static Instant ofEpochMilli(long ms) {
        return new Instant(ms);
    }

    long toEpochMilli() {
        return epochMillis;
    }

    String toString() {
        return "Instant(" + TimeFmt.longToString(epochMillis) + ")";
    }
}

/** Fixed-offset zone (IANA ZoneId foundation — DST rules PARTIAL). */
class ZoneId {
    String id;
    int offsetMinutes;

    ZoneId(String id, int offsetMinutes) {
        this.id = id;
        this.offsetMinutes = offsetMinutes;
    }

    static ZoneId of(String id) {
        if (id == null || id.length() == 0 || id.equals("UTC") || id.equals("Z")) {
            return new ZoneId("UTC", 0);
        }
        if (id.equals("Asia/Shanghai") || id.equals("Asia/Chongqing")) {
            return new ZoneId(id, 480);
        }
        if (id.equals("America/New_York")) {
            return new ZoneId(id, -300);
        }
        if (id.equals("Europe/London")) {
            return new ZoneId(id, 0);
        }
        if (id.length() >= 3 && (id.charAt(0) == 43 || id.charAt(0) == 45)) {
            return parseOffset(id);
        }
        return new ZoneId("UTC", 0);
    }

    static ZoneId UTC() {
        return new ZoneId("UTC", 0);
    }

    static ZoneId parseOffset(String s) {
        int sign = 1;
        int i = 0;
        if (s.charAt(0) == 45) {
            sign = -1;
            i = 1;
        } else if (s.charAt(0) == 43) {
            i = 1;
        }
        int hours = 0;
        int mins = 0;
        if (i + 2 <= s.length()) {
            hours = (s.charAt(i) - 48) * 10 + (s.charAt(i + 1) - 48);
            i = i + 2;
        }
        if (i < s.length() && s.charAt(i) == 58) {
            i = i + 1;
            if (i + 2 <= s.length()) {
                mins = (s.charAt(i) - 48) * 10 + (s.charAt(i + 1) - 48);
            }
        }
        return new ZoneId(s, sign * (hours * 60 + mins));
    }

    int totalOffsetMinutes() {
        return offsetMinutes;
    }

    String toString() {
        return id;
    }
}

class ZonedDateTime {
    long epochMillis;
    ZoneId zone;

    ZonedDateTime(long epochMillis, ZoneId zone) {
        this.epochMillis = epochMillis;
        this.zone = zone;
    }

    static ZonedDateTime ofInstant(Instant instant, ZoneId zone) {
        return new ZonedDateTime(instant.toEpochMilli(), zone);
    }

    LocalDateTime toLocalDateTime() {
        long adj = epochMillis + (zone.offsetMinutes * 1L) * 60000L;
        long dayMs = 86400000L;
        long days = adj / dayMs;
        if (adj < 0 && (adj % dayMs) != 0) {
            days = days - 1;
        }
        long tod = adj - days * dayMs;
        if (tod < 0) {
            tod = tod + dayMs;
        }
        int hour = TimeFmt.longToInt(tod / 3600000L);
        int minute = TimeFmt.longToInt((tod % 3600000L) / 60000L);
        int second = TimeFmt.longToInt((tod % 60000L) / 1000L);
        LocalDate d = CivilDate.fromEpochDay(days);
        return new LocalDateTime(d, LocalTime.of(hour, minute, second));
    }

    String toString() {
        return toLocalDateTime().toString() + "[" + zone.toString() + "]";
    }
}

/** Civil calendar helpers (proleptic Gregorian, UTC day index). */
class CivilDate {
    static int isLeap(int y) {
        if ((y % 400) == 0) {
            return 1;
        }
        if ((y % 100) == 0) {
            return 0;
        }
        if ((y % 4) == 0) {
            return 1;
        }
        return 0;
    }

    static int daysInMonth(int y, int m) {
        if (m == 1 || m == 3 || m == 5 || m == 7 || m == 8 || m == 10 || m == 12) {
            return 31;
        }
        if (m == 4 || m == 6 || m == 9 || m == 11) {
            return 30;
        }
        if (isLeap(y) != 0) {
            return 29;
        }
        return 28;
    }

    static long toEpochDay(int y, int m, int d) {
        long y0 = (y * 1L);
        long m0 = (m * 1L);
        long total = 0L;
        long yy = 1970L;
        while (yy < y0) {
            if (isLeap(TimeFmt.longToInt(yy)) != 0) {
                total = total + 366L;
            } else {
                total = total + 365L;
            }
            yy = yy + 1L;
        }
        while (yy > y0) {
            yy = yy - 1L;
            if (isLeap(TimeFmt.longToInt(yy)) != 0) {
                total = total - 366L;
            } else {
                total = total - 365L;
            }
        }
        int mm = 1;
        while (mm < m) {
            total = total + (daysInMonth(y, mm) * 1L);
            mm = mm + 1;
        }
        total = total + ((d - 1) * 1L);
        return total;
    }

    static LocalDate fromEpochDay(long epochDay) {
        long days = epochDay;
        int y = 1970;
        int more = 1;
        while (more != 0) {
            int diy = 365;
            if (isLeap(y) != 0) {
                diy = 366;
            }
            if (days >= 0 && days < diy) {
                more = 0;
            } else if (days >= diy) {
                days = days - diy;
                y = y + 1;
            } else {
                y = y - 1;
                diy = 365;
                if (isLeap(y) != 0) {
                    diy = 366;
                }
                days = days + diy;
            }
        }
        int m = 1;
        int cont = 1;
        while (cont != 0 && m <= 12) {
            int dim = daysInMonth(y, m);
            if (days < dim) {
                cont = 0;
            } else {
                days = days - dim;
                m = m + 1;
            }
        }
        int d = TimeFmt.longToInt(days) + 1;
        return LocalDate.of(y, m, d);
    }

    static long localDateTimeToEpochMilli(LocalDateTime ldt, ZoneId zone) {
        long day = toEpochDay(ldt.date.year, ldt.date.month, ldt.date.day);
        long tod = ((ldt.time.hour * 1L)) * 3600000L
                + ((ldt.time.minute * 1L)) * 60000L
                + ((ldt.time.second * 1L)) * 1000L;
        return day * 86400000L + tod - (zone.offsetMinutes * 1L) * 60000L;
    }
}

class LocalDate {
    int year;
    int month;
    int day;

    LocalDate(int year, int month, int day) {
        this.year = year;
        this.month = month;
        this.day = day;
    }

    static LocalDate of(int year, int month, int day) {
        return new LocalDate(year, month, day);
    }

    String toString() {
        return pad4(year) + "-" + pad2(month) + "-" + pad2(day);
    }

    static String pad2(int n) {
        if (n < 10) {
            return "0" + String.valueOf(n);
        }
        return String.valueOf(n);
    }

    static String pad4(int n) {
        String s = String.valueOf(n);
        while (s.length() < 4) {
            s = "0" + s;
        }
        return s;
    }
}

class LocalTime {
    int hour;
    int minute;
    int second;

    LocalTime(int hour, int minute, int second) {
        this.hour = hour;
        this.minute = minute;
        this.second = second;
    }

    static LocalTime of(int hour, int minute, int second) {
        return new LocalTime(hour, minute, second);
    }

    String toString() {
        return LocalDate.pad2(hour) + ":" + LocalDate.pad2(minute) + ":" + LocalDate.pad2(second);
    }
}

class LocalDateTime {
    LocalDate date;
    LocalTime time;

    LocalDateTime(LocalDate date, LocalTime time) {
        this.date = date;
        this.time = time;
    }

    String toString() {
        return date.toString() + "T" + time.toString();
    }
}

class Duration {
    long millis;

    Duration(long millis) {
        this.millis = millis;
    }

    static Duration ofMillis(long ms) {
        return new Duration(ms);
    }

    long toMillis() {
        return millis;
    }
}

class UUID {
    long high;
    long low;

    UUID(long high, long low) {
        this.high = high;
        this.low = low;
    }

    static UUID randomUUID() {
        long a = AsyncClockNative.monoMillis();
        long b = a * 1103515245L + 12345L;
        return new UUID(a, b);
    }

    static UUID fromString(String s) {
        if (s == null || s.length() == 0) {
            return new UUID(0L, 0L);
        }
        long hi = 0L;
        int i = 0;
        while (i < s.length()) {
            hi = hi + 1L;
            i = i + 1;
        }
        long lo = hi * 31L;
        return new UUID(hi, lo);
    }

    String toString() {
        return TimeFmt.longToString(high) + "-" + TimeFmt.longToString(low);
    }
}
