// JNative time subset (0.24) — database/Web-facing types only. Not a full java.time clone.

class Instant {
    long epochMillis;

    Instant(long epochMillis) {
        this.epochMillis = epochMillis;
    }

    static Instant now() {
        return new Instant(AsyncClockNative.monoMillis());
    }

    static Instant ofEpochMilli(long ms) {
        return new Instant(ms);
    }

    long toEpochMilli() {
        return epochMillis;
    }

    String toString() {
        return "Instant(" + String.valueOf(epochMillis) + ")";
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
        return String.valueOf(high) + "-" + String.valueOf(low);
    }
}
