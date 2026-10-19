/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// JNative boxed primitives (Integer / Long / Boolean / Float / Double) and Character.
// valueOf caches match Java identity for Integer/Long in [-128, 127] and Boolean TRUE/FALSE.
// Autobox/unbox is applied by the compiler; null unbox -> NullPointerException.
//
// Static "constants" (Integer.MAX_VALUE ...) are ordinary static fields: JNative has no
// final fields yet, so they are assignable and are not usable as `case` labels.
//
// Character classification is exact for ASCII and Latin-1; beyond that it is an
// approximation (common Greek/Cyrillic/CJK/Hangul ranges are treated as letters).

/**
 * Thrown by Integer.parseInt / Long.parseLong / Double.parseDouble on malformed input.
 * @author 张晓达
 */
class NumberFormatException extends IllegalArgumentException {
    NumberFormatException(String message) {
        super(message);
    }
}

class UnsupportedOperationException extends RuntimeException {
    UnsupportedOperationException(String message) {
        super(message);
    }
}

class Integer implements Comparable<Integer> {
    static Integer[] cache;
    static int cacheReady;
    static int MAX_VALUE = 2147483647;
    static int MIN_VALUE = -2147483648;
    static int SIZE = 32;
    static int BYTES = 4;
    int value;

    Integer(int value) {
        this.value = value;
    }

    static void ensureCache() {
        if (cacheReady != 0) {
            return;
        }
        Integer[] c = new Integer[256];
        int i = 0;
        while (i < 256) {
            c[i] = new Integer(i - 128);
            i = i + 1;
        }
        cache = c;
        cacheReady = 1;
    }

    static Integer valueOf(int i) {
        if (i >= -128) {
            if (i <= 127) {
                ensureCache();
                return cache[i + 128];
            }
        }
        return new Integer(i);
    }

    static Integer valueOf(String s) {
        return valueOf(parseInt(s, 10));
    }

    static Integer valueOf(String s, int radix) {
        return valueOf(parseInt(s, radix));
    }

    static int parseInt(String s) {
        return parseInt(s, 10);
    }

    static int parseInt(String s, int radix) {
        return (int) Long.parseBounded(s, radix, -2147483648L, 2147483647L);
    }

    static String toString(int i) {
        return "" + i;
    }

    static String toString(int i, int radix) {
        return Long.toString((long) i, radix);
    }

    static String toHexString(int i) {
        return Long.toUnsignedPow2(((long) i) & 0xFFFFFFFFL, 4);
    }

    static String toOctalString(int i) {
        return Long.toUnsignedPow2(((long) i) & 0xFFFFFFFFL, 3);
    }

    static String toBinaryString(int i) {
        return Long.toUnsignedPow2(((long) i) & 0xFFFFFFFFL, 1);
    }

    static int compare(int a, int b) {
        if (a < b) {
            return -1;
        }
        if (a > b) {
            return 1;
        }
        return 0;
    }

    static int signum(int i) {
        if (i > 0) {
            return 1;
        }
        if (i < 0) {
            return -1;
        }
        return 0;
    }

    static int max(int a, int b) {
        if (a >= b) {
            return a;
        }
        return b;
    }

    static int min(int a, int b) {
        if (a <= b) {
            return a;
        }
        return b;
    }

    static int sum(int a, int b) {
        return a + b;
    }

    static int hashCode(int i) {
        return i;
    }

    static int bitCount(int i) {
        int n = 0;
        int v = i;
        while (v != 0) {
            n = n + (v & 1);
            v = v >>> 1;
        }
        return n;
    }

    static int numberOfLeadingZeros(int i) {
        if (i == 0) {
            return 32;
        }
        int n = 0;
        int v = i;
        while (v > 0) {
            n = n + 1;
            v = v << 1;
        }
        return n;
    }

    static int numberOfTrailingZeros(int i) {
        if (i == 0) {
            return 32;
        }
        int n = 0;
        int v = i;
        while ((v & 1) == 0) {
            n = n + 1;
            v = v >>> 1;
        }
        return n;
    }

    int intValue() {
        return this.value;
    }

    long longValue() {
        return (long) this.value;
    }

    double doubleValue() {
        return (double) this.value;
    }

    public int compareTo(Integer other) {
        return compare(this.value, other.value);
    }

    public String toString() {
        return "" + this.value;
    }

    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (!(other instanceof Integer)) {
            return false;
        }
        Integer o = (Integer) other;
        return this.value == o.value;
    }

    public int hashCode() {
        return this.value;
    }
}

class Long implements Comparable<Long> {
    static Long[] cache;
    static int cacheReady;
    static long MAX_VALUE = 9223372036854775807L;
    static long MIN_VALUE = -9223372036854775808L;
    static int SIZE = 64;
    static int BYTES = 8;
    long value;

    Long(long value) {
        this.value = value;
    }

    static void ensureCache() {
        if (cacheReady != 0) {
            return;
        }
        Long[] c = new Long[256];
        int i = 0;
        while (i < 256) {
            c[i] = new Long((long) (i - 128));
            i = i + 1;
        }
        cache = c;
        cacheReady = 1;
    }

    static Long valueOf(long i) {
        if (i >= -128L) {
            if (i <= 127L) {
                ensureCache();
                return cache[(int) (i + 128L)];
            }
        }
        return new Long(i);
    }

    static Long valueOf(String s) {
        return valueOf(parseLong(s, 10));
    }

    static long parseLong(String s) {
        return parseLong(s, 10);
    }

    static long parseLong(String s, int radix) {
        return parseBounded(s, radix, -9223372036854775808L, 9223372036854775807L);
    }

    /**
     * Shared by Integer.parseInt / Long.parseLong. Accumulates negatively so that MIN_VALUE
     * parses without overflow (same technique as the JDK).
     */
    static long parseBounded(String s, int radix, long minValue, long maxValue) {
        if (s == null) {
            throw new NumberFormatException("Cannot parse null string");
        }
        if (radix < 2) {
            throw new NumberFormatException("radix " + radix + " less than Character.MIN_RADIX");
        }
        if (radix > 36) {
            throw new NumberFormatException("radix " + radix + " greater than Character.MAX_RADIX");
        }
        int len = s.length();
        if (len == 0) {
            throw formatError(s, radix);
        }
        boolean negative = false;
        int i = 0;
        long limit = -maxValue;
        char first = s.charAt(0);
        if (first == '-' || first == '+') {
            if (len == 1) {
                throw formatError(s, radix);
            }
            if (first == '-') {
                negative = true;
                limit = minValue;
            }
            i = 1;
        }
        long r = (long) radix;
        long multmin = limit / r;
        long result = 0L;
        while (i < len) {
            int d = Character.digit(s.charAt(i), radix);
            if (d < 0) {
                throw formatError(s, radix);
            }
            if (result < multmin) {
                throw formatError(s, radix);
            }
            result = result * r;
            if (result < limit + (long) d) {
                throw formatError(s, radix);
            }
            result = result - (long) d;
            i = i + 1;
        }
        if (negative) {
            return result;
        }
        return -result;
    }

    static NumberFormatException formatError(String s, int radix) {
        String m = "For input string: \"" + s + "\"";
        if (radix != 10) {
            m = m + " under radix " + radix;
        }
        return new NumberFormatException(m);
    }

    static String toString(long i) {
        return "" + i;
    }

    static String toString(long i, int radix) {
        if (radix < 2 || radix > 36) {
            radix = 10;
        }
        if (radix == 10) {
            return "" + i;
        }
        if (i == 0L) {
            return "0";
        }
        boolean negative = i < 0L;
        long v = i;
        if (!negative) {
            v = -v;
        }
        long r = (long) radix;
        StringBuilder sb = new StringBuilder();
        while (v != 0L) {
            int d = (int) (-(v % r));
            sb.append(Character.forDigit(d, radix));
            v = v / r;
        }
        if (negative) {
            sb.append('-');
        }
        sb.reverse();
        return sb.toString();
    }

    /** v is read as an unsigned 64-bit value; shift is log2(radix). */
    static String toUnsignedPow2(long v, int shift) {
        if (v == 0L) {
            return "0";
        }
        long mask = (1L << shift) - 1L;
        StringBuilder sb = new StringBuilder();
        long x = v;
        while (x != 0L) {
            int d = (int) (x & mask);
            sb.append(Character.forDigit(d, 1 << shift));
            x = x >>> shift;
        }
        sb.reverse();
        return sb.toString();
    }

    static String toHexString(long i) {
        return toUnsignedPow2(i, 4);
    }

    static String toOctalString(long i) {
        return toUnsignedPow2(i, 3);
    }

    static String toBinaryString(long i) {
        return toUnsignedPow2(i, 1);
    }

    static int compare(long a, long b) {
        if (a < b) {
            return -1;
        }
        if (a > b) {
            return 1;
        }
        return 0;
    }

    static int signum(long i) {
        if (i > 0L) {
            return 1;
        }
        if (i < 0L) {
            return -1;
        }
        return 0;
    }

    static long max(long a, long b) {
        if (a >= b) {
            return a;
        }
        return b;
    }

    static long min(long a, long b) {
        if (a <= b) {
            return a;
        }
        return b;
    }

    static long sum(long a, long b) {
        return a + b;
    }

    static int hashCode(long v) {
        return (int) (v ^ (v >>> 32));
    }

    static int bitCount(long i) {
        int n = 0;
        long v = i;
        while (v != 0L) {
            n = n + (int) (v & 1L);
            v = v >>> 1;
        }
        return n;
    }

    long longValue() {
        return this.value;
    }

    int intValue() {
        return (int) this.value;
    }

    double doubleValue() {
        return (double) this.value;
    }

    public int compareTo(Long other) {
        return compare(this.value, other.value);
    }

    public String toString() {
        return "" + this.value;
    }

    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (!(other instanceof Long)) {
            return false;
        }
        Long o = (Long) other;
        return this.value == o.value;
    }

    public int hashCode() {
        return (int) (this.value ^ (this.value >>> 32));
    }
}

class Boolean implements Comparable<Boolean> {
    static Boolean TRUE = new Boolean(true);
    static Boolean FALSE = new Boolean(false);
    boolean value;

    Boolean(boolean value) {
        this.value = value;
    }

    static Boolean valueOf(boolean b) {
        if (b) {
            return TRUE;
        }
        return FALSE;
    }

    static Boolean valueOf(String s) {
        return valueOf(parseBoolean(s));
    }

    static boolean parseBoolean(String s) {
        if (s == null) {
            return false;
        }
        return s.equalsIgnoreCase("true");
    }

    static String toString(boolean b) {
        if (b) {
            return "true";
        }
        return "false";
    }

    static int compare(boolean a, boolean b) {
        if (a == b) {
            return 0;
        }
        if (a) {
            return 1;
        }
        return -1;
    }

    static boolean logicalAnd(boolean a, boolean b) {
        return a && b;
    }

    static boolean logicalOr(boolean a, boolean b) {
        return a || b;
    }

    static boolean logicalXor(boolean a, boolean b) {
        return a != b;
    }

    static int hashCode(boolean b) {
        if (b) {
            return 1231;
        }
        return 1237;
    }

    boolean booleanValue() {
        return this.value;
    }

    public int compareTo(Boolean other) {
        return compare(this.value, other.value);
    }

    public String toString() {
        if (this.value) {
            return "true";
        }
        return "false";
    }

    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (!(other instanceof Boolean)) {
            return false;
        }
        Boolean o = (Boolean) other;
        return this.value == o.value;
    }

    public int hashCode() {
        if (this.value) {
            return 1231;
        }
        return 1237;
    }
}

class Float implements Comparable<Float> {
    static float MAX_VALUE = 3.4028235E38f;
    static float MIN_VALUE = 1.4E-45f;
    float value;

    Float(float value) {
        this.value = value;
    }

    static Float valueOf(float f) {
        return new Float(f);
    }

    static Float valueOf(String s) {
        return new Float(parseFloat(s));
    }

    static float parseFloat(String s) {
        return (float) Double.parseDouble(s);
    }

    static String toString(float f) {
        return "" + f;
    }

    static boolean isNaN(float f) {
        return f != f;
    }

    static int compare(float a, float b) {
        return Double.compare((double) a, (double) b);
    }

    static float max(float a, float b) {
        if (a >= b) {
            return a;
        }
        return b;
    }

    static float min(float a, float b) {
        if (a <= b) {
            return a;
        }
        return b;
    }

    static float sum(float a, float b) {
        return a + b;
    }

    float floatValue() {
        return this.value;
    }

    double doubleValue() {
        return (double) this.value;
    }

    int intValue() {
        return (int) this.value;
    }

    boolean isNaN() {
        return this.value != this.value;
    }

    public int compareTo(Float other) {
        return compare(this.value, other.value);
    }

    public String toString() {
        return "" + this.value;
    }

    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (!(other instanceof Float)) {
            return false;
        }
        Float o = (Float) other;
        return this.value == o.value;
    }

    public int hashCode() {
        // Not bit-identical to Java floatToIntBits; adequate for map keys of equal values.
        return (int) this.value;
    }
}

class Double implements Comparable<Double> {
    static double MAX_VALUE = 1.7976931348623157E308;
    static double MIN_VALUE = 4.9E-324;
    static double POSITIVE_INFINITY = 1.0 / 0.0;
    static double NEGATIVE_INFINITY = -1.0 / 0.0;
    static double NaN = 0.0 / 0.0;
    static double[] pow10Table;
    double value;

    Double(double value) {
        this.value = value;
    }

    static Double valueOf(double d) {
        return new Double(d);
    }

    static Double valueOf(String s) {
        return new Double(parseDouble(s));
    }

    static String toString(double d) {
        return "" + d;
    }

    static boolean isNaN(double d) {
        return d != d;
    }

    static boolean isInfinite(double d) {
        return d == POSITIVE_INFINITY || d == NEGATIVE_INFINITY;
    }

    static boolean isFinite(double d) {
        return d == d && d != POSITIVE_INFINITY && d != NEGATIVE_INFINITY;
    }

    /** Java ordering: -0.0 < 0.0 and NaN is greater than everything (including itself). */
    static int compare(double a, double b) {
        if (a < b) {
            return -1;
        }
        if (a > b) {
            return 1;
        }
        if (a == b) {
            if (a == 0.0) {
                double ia = 1.0 / a;
                double ib = 1.0 / b;
                if (ia < ib) {
                    return -1;
                }
                if (ia > ib) {
                    return 1;
                }
            }
            return 0;
        }
        boolean an = a != a;
        boolean bn = b != b;
        if (an) {
            if (bn) {
                return 0;
            }
            return 1;
        }
        return -1;
    }

    static double max(double a, double b) {
        if (a != a) {
            return a;
        }
        if (a >= b) {
            return a;
        }
        return b;
    }

    static double min(double a, double b) {
        if (a != a) {
            return a;
        }
        if (a <= b) {
            return a;
        }
        return b;
    }

    static double sum(double a, double b) {
        return a + b;
    }

    static void ensurePow10() {
        if (pow10Table != null) {
            return;
        }
        double[] t = new double[23];
        double p = 1.0;
        int i = 0;
        while (i < 23) {
            t[i] = p;
            p = p * 10.0;
            i = i + 1;
        }
        pow10Table = t;
    }

    /** 10^n for n >= 0 by binary exponentiation (exact up to 10^22, a few ulp beyond). */
    static double pow10(int n) {
        ensurePow10();
        if (n <= 22) {
            return pow10Table[n];
        }
        double result = 1.0;
        double base = 10.0;
        int e = n;
        while (e > 0) {
            if ((e & 1) != 0) {
                result = result * base;
            }
            base = base * base;
            e = e >> 1;
        }
        return result;
    }

    static NumberFormatException formatError(String s) {
        return new NumberFormatException("For input string: \"" + s + "\"");
    }

    /**
     * Java-grammar decimal parser: optional sign, digits[.digits][e[sign]digits][fFdD], NaN,
     * Infinity; surrounding whitespace is trimmed. Hex floating literals are not supported.
     * Results with at most 15..18 significant digits and |exponent| <= 22 are correctly rounded
     * (Clinger fast path); other inputs may differ from the JDK by 1-2 ulp.
     */
    static double parseDouble(String s) {
        if (s == null) {
            throw new NullPointerException();
        }
        String t = s.trim();
        int n = t.length();
        if (n == 0) {
            throw new NumberFormatException("empty String");
        }
        int i = 0;
        boolean neg = false;
        char c = t.charAt(0);
        if (c == '-' || c == '+') {
            neg = c == '-';
            i = 1;
        }
        String rest = t.substring(i, n);
        if (rest.equals("NaN")) {
            return NaN;
        }
        if (rest.equals("Infinity")) {
            if (neg) {
                return NEGATIVE_INFINITY;
            }
            return POSITIVE_INFINITY;
        }
        long mant = 0L;
        int dropped = 0;
        int fracDigits = 0;
        boolean inexact = false;
        boolean anyDigit = false;
        while (i < n) {
            c = t.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            anyDigit = true;
            if (mant < 100000000000000000L) {
                mant = mant * 10L + (long) (c - '0');
            } else {
                dropped = dropped + 1;
                if (c != '0') {
                    inexact = true;
                }
            }
            i = i + 1;
        }
        if (i < n && t.charAt(i) == '.') {
            i = i + 1;
            while (i < n) {
                c = t.charAt(i);
                if (c < '0' || c > '9') {
                    break;
                }
                anyDigit = true;
                if (mant < 100000000000000000L) {
                    mant = mant * 10L + (long) (c - '0');
                    fracDigits = fracDigits + 1;
                } else if (c != '0') {
                    inexact = true;
                }
                i = i + 1;
            }
        }
        if (i < n && t.charAt(i) == '.') {
            throw new NumberFormatException("multiple points");
        }
        if (!anyDigit) {
            throw formatError(s);
        }
        int exp = 0;
        if (i < n && (t.charAt(i) == 'e' || t.charAt(i) == 'E')) {
            i = i + 1;
            boolean expNeg = false;
            if (i < n && (t.charAt(i) == '-' || t.charAt(i) == '+')) {
                expNeg = t.charAt(i) == '-';
                i = i + 1;
            }
            boolean expDigit = false;
            while (i < n) {
                c = t.charAt(i);
                if (c < '0' || c > '9') {
                    break;
                }
                expDigit = true;
                if (exp < 100000) {
                    exp = exp * 10 + (c - '0');
                }
                i = i + 1;
            }
            if (!expDigit) {
                throw formatError(s);
            }
            if (expNeg) {
                exp = -exp;
            }
        }
        if (i < n) {
            c = t.charAt(i);
            if (c == 'f' || c == 'F' || c == 'd' || c == 'D') {
                i = i + 1;
            }
        }
        if (i != n) {
            throw formatError(s);
        }
        double result = scale(mant, exp - fracDigits + dropped, inexact);
        if (neg) {
            return -result;
        }
        return result;
    }

    static double scale(long mant, int exp10, boolean inexact) {
        if (mant == 0L) {
            return 0.0;
        }
        double m = (double) mant;
        if (!inexact && mant <= 9007199254740992L) {
            if (exp10 == 0) {
                return m;
            }
            if (exp10 > 0 && exp10 <= 22) {
                return m * pow10(exp10);
            }
            if (exp10 < 0 && exp10 >= -22) {
                return m / pow10(-exp10);
            }
        }
        // Slow path: split huge/tiny exponents so intermediate results do not overflow early.
        double r = m;
        int e = exp10;
        while (e > 300) {
            r = r * 1.0E300;
            e = e - 300;
            if (r == POSITIVE_INFINITY) {
                return r;
            }
        }
        while (e < -300) {
            r = r / 1.0E300;
            e = e + 300;
            if (r == 0.0) {
                return r;
            }
        }
        if (e >= 0) {
            return r * pow10(e);
        }
        return r / pow10(-e);
    }

    static double parseDoubleOrDefault(String s, double fallback) {
        try {
            return parseDouble(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    double doubleValue() {
        return this.value;
    }

    float floatValue() {
        return (float) this.value;
    }

    int intValue() {
        return (int) this.value;
    }

    long longValue() {
        return (long) this.value;
    }

    boolean isNaN() {
        return this.value != this.value;
    }

    boolean isInfinite() {
        return isInfinite(this.value);
    }

    public int compareTo(Double other) {
        return compare(this.value, other.value);
    }

    public String toString() {
        return "" + this.value;
    }

    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (!(other instanceof Double)) {
            return false;
        }
        Double o = (Double) other;
        return this.value == o.value;
    }

    public int hashCode() {
        long bits = (long) this.value;
        return (int) bits;
    }
}

class Character implements Comparable<Character> {
    static int MIN_RADIX = 2;
    static int MAX_RADIX = 36;
    static char MIN_VALUE = '\u0000';
    static char MAX_VALUE = '\uffff';
    char value;

    Character(char value) {
        this.value = value;
    }

    static Character valueOf(char c) {
        return new Character(c);
    }

    static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    static boolean isUpperCase(char c) {
        if (c >= 'A' && c <= 'Z') {
            return true;
        }
        if (c >= 0xC0 && c <= 0xDE) {
            return c != 0xD7;
        }
        return false;
    }

    static boolean isLowerCase(char c) {
        if (c >= 'a' && c <= 'z') {
            return true;
        }
        if (c >= 0xDF && c <= 0xFF) {
            return c != 0xF7;
        }
        return c == 0xAA || c == 0xB5 || c == 0xBA;
    }

    static boolean isLetter(char c) {
        if (c < 128) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
        }
        if (c < 0x100) {
            if (c == 0xAA || c == 0xB5 || c == 0xBA) {
                return true;
            }
            return c >= 0xC0 && c != 0xD7 && c != 0xF7;
        }
        if (c >= 0x100 && c <= 0x2AF) {
            return true;
        }
        if (c >= 0x370 && c <= 0x52F) {
            return c != 0x37E && c != 0x387 && c != 0x375;
        }
        if (c >= 0x3040 && c <= 0x30FF) {
            return c != 0x30FB && c != 0x3099 && c != 0x309A && c != 0x309B && c != 0x309C;
        }
        if (c >= 0x3400 && c <= 0x4DBF) {
            return true;
        }
        if (c >= 0x4E00 && c <= 0x9FFF) {
            return true;
        }
        return c >= 0xAC00 && c <= 0xD7A3;
    }

    static boolean isLetterOrDigit(char c) {
        return isLetter(c) || isDigit(c);
    }

    static boolean isAlphabetic(char c) {
        return isLetter(c);
    }

    /** Java isWhitespace: excludes the non-breaking spaces U+00A0, U+2007, U+202F. */
    static boolean isWhitespace(char c) {
        if (c == ' ' || (c >= 9 && c <= 13) || (c >= 0x1C && c <= 0x1F)) {
            return true;
        }
        if (c < 128) {
            return false;
        }
        if (c == 0x1680 || c == 0x2028 || c == 0x2029 || c == 0x205F || c == 0x3000) {
            return true;
        }
        return c >= 0x2000 && c <= 0x200A && c != 0x2007;
    }

    static boolean isSpaceChar(char c) {
        return c == ' ' || c == 0xA0 || c == 0x1680 || (c >= 0x2000 && c <= 0x200A)
                || c == 0x2028 || c == 0x2029 || c == 0x202F || c == 0x205F || c == 0x3000;
    }

    static char toUpperCase(char c) {
        if (c >= 'a' && c <= 'z') {
            return (char) (c - 32);
        }
        if (c >= 0xE0 && c <= 0xFE && c != 0xF7) {
            return (char) (c - 32);
        }
        return c;
    }

    static char toLowerCase(char c) {
        if (c >= 'A' && c <= 'Z') {
            return (char) (c + 32);
        }
        if (c >= 0xC0 && c <= 0xDE && c != 0xD7) {
            return (char) (c + 32);
        }
        return c;
    }

    /** Digit value of c in the radix, or -1. Accepts ASCII digits and letters only. */
    static int digit(char c, int radix) {
        if (radix < 2 || radix > 36) {
            return -1;
        }
        int d = -1;
        if (c >= '0' && c <= '9') {
            d = c - '0';
        } else if (c >= 'a' && c <= 'z') {
            d = c - 'a' + 10;
        } else if (c >= 'A' && c <= 'Z') {
            d = c - 'A' + 10;
        }
        if (d < radix) {
            return d;
        }
        return -1;
    }

    static char forDigit(int digit, int radix) {
        if (radix < 2 || radix > 36 || digit < 0 || digit >= radix) {
            return '\u0000';
        }
        if (digit < 10) {
            return (char) ('0' + digit);
        }
        return (char) ('a' + digit - 10);
    }

    static int getNumericValue(char c) {
        return digit(c, 36);
    }

    static String toString(char c) {
        return String.valueOf(c);
    }

    static int compare(char a, char b) {
        return a - b;
    }

    static int hashCode(char c) {
        return c;
    }

    char charValue() {
        return this.value;
    }

    public int compareTo(Character other) {
        return this.value - other.value;
    }

    public String toString() {
        return String.valueOf(this.value);
    }

    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (!(other instanceof Character)) {
            return false;
        }
        Character o = (Character) other;
        return this.value == o.value;
    }

    public int hashCode() {
        return this.value;
    }
}
