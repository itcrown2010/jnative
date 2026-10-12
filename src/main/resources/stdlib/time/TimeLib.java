/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// JNative Time Core (Round 2) — minimum java.time-compatible profile.
// Wall clock vs monotonic: Instant/Clock use wall; Duration measurement should use Clock.monotonic / monoNanos.
// ZoneId named zones resolve offsets via platform IANA zoneinfo (jnative_tz_*).

class TimeClockNative {
    @Native(symbol = "jnative_wall_millis")
    static native long wallMillis();

    @Native(symbol = "jnative_web_mono_millis")
    static native long monoMillis();

    @Native(symbol = "jnative_mono_nanos")
    static native long monoNanos();

    @Native(symbol = "jnative_tz_offset_minutes_at")
    static native int tzOffsetMinutesAt(String zoneId, long epochMs);

    @Native(symbol = "jnative_tz_exists")
    static native int tzExists(String zoneId);
}

class TimeFmt {
    static String longToString(long v) {
        if (v == 0L) {
            return "0";
        }
        String out = "";
        int neg = 0;
        if (v < 0L) {
            neg = 1;
            v = 0L - v;
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
        int neg = 0;
        if (v < 0L) {
            neg = 1;
            v = 0L - v;
        }
        while (v > 0L && out < 2147483647) {
            out = out + 1;
            v = v - 1L;
        }
        if (neg != 0) {
            return 0 - out;
        }
        return out;
    }

    static String pad2(int n) {
        if (n < 0) {
            n = 0 - n;
        }
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

/**
 * Instant — UTC timeline point as epoch milliseconds.
 * Instant.now() uses wall clock (CLOCK_REALTIME). Use Clock.monotonic() for elapsed time.
 * @author 张晓达
 */
class Instant {
    long epochMillis;

    Instant(long epochMillis) {
        this.epochMillis = epochMillis;
    }

    static Instant now() {
        return new Instant(TimeClockNative.wallMillis());
    }

    static Instant ofEpochMilli(long ms) {
        return new Instant(ms);
    }

    static Instant ofEpochSecond(long seconds) {
        return new Instant(seconds * 1000L);
    }

    long toEpochMilli() {
        return this.epochMillis;
    }

    Instant plus(Duration d) {
        return new Instant(this.epochMillis + d.toMillis());
    }

    Instant minus(Duration d) {
        return new Instant(this.epochMillis - d.toMillis());
    }

    Instant plusMillis(long ms) {
        return new Instant(this.epochMillis + ms);
    }

    Instant minusMillis(long ms) {
        return new Instant(this.epochMillis - ms);
    }

    int compareTo(Instant other) {
        if (this.epochMillis < other.epochMillis) {
            return 0 - 1;
        }
        if (this.epochMillis > other.epochMillis) {
            return 1;
        }
        return 0;
    }

    boolean equals(Instant other) {
        if (other == null) {
            return false;
        }
        return this.epochMillis == other.epochMillis;
    }

    String toString() {
        return "Instant(" + TimeFmt.longToString(this.epochMillis) + ")";
    }
}

/**
 * Duration — millisecond precision in Round 2.
 * Overflow: ofDays/ofHours that exceed long range are not detected; prefer ofMillis for large values.
 */
class Duration {
    long millis;

    Duration(long millis) {
        this.millis = millis;
    }

    static Duration ofNanos(long nanos) {
        return new Duration(nanos / 1000000L);
    }

    static Duration ofMillis(long ms) {
        return new Duration(ms);
    }

    static Duration ofSeconds(long seconds) {
        return new Duration(seconds * 1000L);
    }

    static Duration ofMinutes(long minutes) {
        return new Duration(minutes * 60000L);
    }

    static Duration ofHours(long hours) {
        return new Duration(hours * 3600000L);
    }

    static Duration ofDays(long days) {
        return new Duration(days * 86400000L);
    }

    static Duration between(Instant a, Instant b) {
        return new Duration(b.toEpochMilli() - a.toEpochMilli());
    }

    Duration plus(Duration other) {
        return new Duration(this.millis + other.millis);
    }

    Duration minus(Duration other) {
        return new Duration(this.millis - other.millis);
    }

    long toMillis() {
        return this.millis;
    }

    long toSeconds() {
        return this.millis / 1000L;
    }

    int compareTo(Duration other) {
        if (this.millis < other.millis) {
            return 0 - 1;
        }
        if (this.millis > other.millis) {
            return 1;
        }
        return 0;
    }
}

/** Fixed offset from UTC. */
class ZoneOffset {
    int totalSeconds;

    ZoneOffset(int totalSeconds) {
        this.totalSeconds = totalSeconds;
    }

    static ZoneOffset UTC() {
        return new ZoneOffset(0);
    }

    static ZoneOffset of(String text) {
        ZoneId z = ZoneId.of(text);
        return new ZoneOffset(z.getTotalOffsetMinutes(0L) * 60);
    }

    static ZoneOffset ofHours(int hours) {
        return new ZoneOffset(hours * 3600);
    }

    static ZoneOffset ofHoursMinutes(int hours, int minutes) {
        int sign = 1;
        if (hours < 0) {
            sign = 0 - 1;
            hours = 0 - hours;
        }
        return new ZoneOffset(sign * (hours * 3600 + minutes * 60));
    }

    int getTotalSeconds() {
        return this.totalSeconds;
    }

    int getTotalMinutes() {
        return this.totalSeconds / 60;
    }

    String toString() {
        if (this.totalSeconds == 0) {
            return "Z";
        }
        int sign = 1;
        int sec = this.totalSeconds;
        if (sec < 0) {
            sign = 0 - 1;
            sec = 0 - sec;
        }
        int h = sec / 3600;
        int m = (sec % 3600) / 60;
        String pref = "+";
        if (sign < 0) {
            pref = "-";
        }
        return pref + TimeFmt.pad2(h) + ":" + TimeFmt.pad2(m);
    }
}

/**
 * ZoneId — named IANA zones use platform zoneinfo; fixed offsets parsed locally.
 * DST: offsetMinutesAt(epochMs) returns the correct offset including DST transitions.
 *
 * Local → Instant gap/overlap policy (Round 2):
 * - Gap (spring forward): use the offset after the transition (post-gap).
 * - Overlap (fall back): use the earlier offset (daylight / first occurrence).
 */
class ZoneId {
    String id;
    int fixedOffsetMinutes;
    int isFixed;

    ZoneId(String id, int fixedOffsetMinutes, int isFixed) {
        this.id = id;
        this.fixedOffsetMinutes = fixedOffsetMinutes;
        this.isFixed = isFixed;
    }

    static ZoneId of(String id) {
        if (id == null || id.length() == 0 || id.equals("UTC") || id.equals("Z")) {
            return new ZoneId("UTC", 0, 1);
        }
        if (id.length() >= 3 && (id.charAt(0) == 43 || id.charAt(0) == 45)) {
            return parseFixed(id);
        }
        if (TimeClockNative.tzExists(id) == 0) {
            throw new IllegalArgumentException("Unknown ZoneId: " + id);
        }
        return new ZoneId(id, 0, 0);
    }

    static ZoneId UTC() {
        return new ZoneId("UTC", 0, 1);
    }

    static ZoneId parseFixed(String s) {
        int sign = 1;
        int i = 0;
        if (s.charAt(0) == 45) {
            sign = 0 - 1;
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
        return new ZoneId(s, sign * (hours * 60 + mins), 1);
    }

    int getTotalOffsetMinutes(long epochMs) {
        if (this.isFixed != 0) {
            return this.fixedOffsetMinutes;
        }
        int off = TimeClockNative.tzOffsetMinutesAt(this.id, epochMs);
        /* Platform failure sentinel INT32_MIN; valid civil offsets are within ±18h. */
        if (off < 0 - 20000 || off > 20000) {
            throw new IllegalArgumentException("Zone offset lookup failed: " + this.id);
        }
        return off;
    }

    String getId() {
        return this.id;
    }

    String toString() {
        return this.id;
    }
}

class Clock {
    Instant fixedInstant;
    long offsetMillis;
    int mode;

    Clock(Instant fixedInstant, long offsetMillis, int mode) {
        this.fixedInstant = fixedInstant;
        this.offsetMillis = offsetMillis;
        this.mode = mode;
    }

    /** mode 0 = system UTC wall, 1 = fixed, 2 = offset from system. */
    static Clock systemUTC() {
        return new Clock(null, 0L, 0);
    }

    static Clock fixed(Instant instant) {
        return new Clock(instant, 0L, 1);
    }

    static Clock offset(Clock base, Duration offset) {
        Instant now = base.instant();
        return new Clock(now, offset.toMillis(), 2);
    }

    Instant instant() {
        if (this.mode == 1) {
            return this.fixedInstant;
        }
        long wall = TimeClockNative.wallMillis();
        if (this.mode == 2) {
            return Instant.ofEpochMilli(wall + this.offsetMillis);
        }
        return Instant.ofEpochMilli(wall);
    }

    static long monotonicMillis() {
        return TimeClockNative.monoMillis();
    }

    static long monotonicNanos() {
        return TimeClockNative.monoNanos();
    }
}

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
        long y0 = y * 1L;
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
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid month");
        }
        int dim = CivilDate.daysInMonth(year, month);
        if (day < 1 || day > dim) {
            throw new IllegalArgumentException("Invalid day");
        }
        return new LocalDate(year, month, day);
    }

    int getYear() {
        return this.year;
    }

    int getMonth() {
        return this.month;
    }

    int getDay() {
        return this.day;
    }

    boolean isLeapYear() {
        return CivilDate.isLeap(this.year) != 0;
    }

    int lengthOfMonth() {
        return CivilDate.daysInMonth(this.year, this.month);
    }

    LocalDate plusDays(long days) {
        long ed = CivilDate.toEpochDay(this.year, this.month, this.day) + days;
        return CivilDate.fromEpochDay(ed);
    }

    LocalDate minusDays(long days) {
        return plusDays(0L - days);
    }

    LocalDate plusMonths(long months) {
        long total = (this.year * 1L) * 12L + ((this.month - 1) * 1L) + months;
        int y = TimeFmt.longToInt(total / 12L);
        int m = TimeFmt.longToInt(total - (total / 12L) * 12L) + 1;
        int dim = CivilDate.daysInMonth(y, m);
        int d = this.day;
        if (d > dim) {
            d = dim;
        }
        return LocalDate.of(y, m, d);
    }

    LocalDate minusMonths(long months) {
        return plusMonths(0L - months);
    }

    LocalDate plusYears(long years) {
        return plusMonths(years * 12L);
    }

    LocalDate minusYears(long years) {
        return plusYears(0L - years);
    }

    int compareTo(LocalDate other) {
        if (this.year != other.year) {
            if (this.year < other.year) {
                return 0 - 1;
            }
            return 1;
        }
        if (this.month != other.month) {
            if (this.month < other.month) {
                return 0 - 1;
            }
            return 1;
        }
        if (this.day < other.day) {
            return 0 - 1;
        }
        if (this.day > other.day) {
            return 1;
        }
        return 0;
    }

    String toString() {
        return TimeFmt.pad4(this.year) + "-" + TimeFmt.pad2(this.month) + "-" + TimeFmt.pad2(this.day);
    }
}

class LocalTime {
    int hour;
    int minute;
    int second;
    int nano;

    LocalTime(int hour, int minute, int second, int nano) {
        this.hour = hour;
        this.minute = minute;
        this.second = second;
        this.nano = nano;
    }

    static LocalTime of(int hour, int minute, int second) {
        return of(hour, minute, second, 0);
    }

    static LocalTime of(int hour, int minute, int second, int nano) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59 || second < 0 || second > 59) {
            throw new IllegalArgumentException("Invalid LocalTime");
        }
        return new LocalTime(hour, minute, second, nano);
    }

    int getHour() {
        return this.hour;
    }

    int getMinute() {
        return this.minute;
    }

    int getSecond() {
        return this.second;
    }

    int getNano() {
        return this.nano;
    }

    LocalTime plusHours(long hours) {
        long h = ((this.hour * 1L) + hours) % 24L;
        if (h < 0L) {
            h = h + 24L;
        }
        return LocalTime.of(TimeFmt.longToInt(h), this.minute, this.second, this.nano);
    }

    LocalTime plusMinutes(long minutes) {
        long total = (this.hour * 60L) + (this.minute * 1L) + minutes;
        long dayMins = 24L * 60L;
        total = total % dayMins;
        if (total < 0L) {
            total = total + dayMins;
        }
        int h = TimeFmt.longToInt(total / 60L);
        int m = TimeFmt.longToInt(total - (total / 60L) * 60L);
        return LocalTime.of(h, m, this.second, this.nano);
    }

    LocalTime plusSeconds(long seconds) {
        long total = (this.hour * 3600L) + (this.minute * 60L) + (this.second * 1L) + seconds;
        long day = 86400L;
        total = total % day;
        if (total < 0L) {
            total = total + day;
        }
        int h = TimeFmt.longToInt(total / 3600L);
        int rem = TimeFmt.longToInt(total - (total / 3600L) * 3600L);
        int m = rem / 60;
        int s = rem - m * 60;
        return LocalTime.of(h, m, s, this.nano);
    }

    String toString() {
        return TimeFmt.pad2(this.hour) + ":" + TimeFmt.pad2(this.minute) + ":" + TimeFmt.pad2(this.second);
    }
}

class LocalDateTime {
    LocalDate date;
    LocalTime time;

    LocalDateTime(LocalDate date, LocalTime time) {
        this.date = date;
        this.time = time;
    }

    static LocalDateTime of(LocalDate date, LocalTime time) {
        return new LocalDateTime(date, time);
    }

    static LocalDateTime of(int y, int m, int d, int h, int mi, int s) {
        return new LocalDateTime(LocalDate.of(y, m, d), LocalTime.of(h, mi, s));
    }

    LocalDate toLocalDate() {
        return this.date;
    }

    LocalTime toLocalTime() {
        return this.time;
    }

    LocalDateTime plusDays(long days) {
        return LocalDateTime.of(this.date.plusDays(days), this.time);
    }

    LocalDateTime plusHours(long hours) {
        long total = (this.time.hour * 1L) + hours;
        long dayAdd = 0L;
        if (total >= 24L) {
            dayAdd = total / 24L;
            total = total - dayAdd * 24L;
        }
        if (total < 0L) {
            dayAdd = (total / 24L) - 1L;
            if ((total % 24L) == 0L) {
                dayAdd = total / 24L;
            }
            total = total - dayAdd * 24L;
        }
        LocalTime t = LocalTime.of(TimeFmt.longToInt(total), this.time.minute, this.time.second, this.time.nano);
        return LocalDateTime.of(this.date.plusDays(dayAdd), t);
    }

    String toString() {
        return this.date.toString() + "T" + this.time.toString();
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

    Instant toInstant() {
        return Instant.ofEpochMilli(this.epochMillis);
    }

    ZoneId getZone() {
        return this.zone;
    }

    LocalDateTime toLocalDateTime() {
        int off = this.zone.getTotalOffsetMinutes(this.epochMillis);
        long adj = this.epochMillis + (off * 1L) * 60000L;
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
        return toLocalDateTime().toString() + "[" + this.zone.toString() + "]";
    }
}

/** ISO-8601 basics: yyyy-MM-dd, HH:mm:ss, yyyy-MM-dd'T'HH:mm:ss. */
class DateTimeFormatter {
    static String formatLocalDate(LocalDate d) {
        return d.toString();
    }

    static String formatLocalTime(LocalTime t) {
        return t.toString();
    }

    static String formatLocalDateTime(LocalDateTime ldt) {
        return ldt.toString();
    }

    static LocalDate parseLocalDate(String s) {
        if (s == null || s.length() < 10) {
            throw new IllegalArgumentException("Bad LocalDate");
        }
        int y = (s.charAt(0) - 48) * 1000 + (s.charAt(1) - 48) * 100
                + (s.charAt(2) - 48) * 10 + (s.charAt(3) - 48);
        int m = (s.charAt(5) - 48) * 10 + (s.charAt(6) - 48);
        int d = (s.charAt(8) - 48) * 10 + (s.charAt(9) - 48);
        return LocalDate.of(y, m, d);
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
        long a = TimeClockNative.monoMillis();
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
        return new UUID(hi, hi * 31L);
    }

    String toString() {
        return TimeFmt.longToString(this.high) + "-" + TimeFmt.longToString(this.low);
    }
}
