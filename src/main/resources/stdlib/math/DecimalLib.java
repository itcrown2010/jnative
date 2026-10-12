/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// JNative Decimal Core (0.33) — reusable base-10 arithmetic (no BigDecimal JVM type).
// Value = sign * digits / 10^scale, where digits is an absolute decimal integer string.
// Rounding: ROUND_HALF_UP. Division uses configurable scale (default 32).

class DecimalException extends RuntimeException {
    DecimalException(String message) {
        super(message);
    }
}

class Decimal {
    int sign;
    String digits;
    int scale;

    static int ROUND_HALF_UP = 0;
    static int DEFAULT_DIV_SCALE = 32;
    static int MAX_DIGITS = 128;

    static Decimal ZERO = Decimal.fromLong(0L);
    static Decimal ONE = Decimal.fromLong(1L);

    Decimal(int s, String d, int sc) {
        if (s < 0) {
            sign = -1;
        } else {
            sign = 1;
        }
        if (d == null || d.length() == 0) {
            digits = "0";
        } else {
            digits = DecimalDigits.stripLeadingZeros(d);
        }
        if (digits.equals("0")) {
            sign = 1;
        }
        scale = sc;
        if (scale < 0) {
            scale = 0;
        }
    }

    static Decimal fromLong(long v) {
        if (v == 0L) {
            return new Decimal(1, "0", 0);
        }
        int s = 1;
        long n = v;
        if (n < 0L) {
            s = -1;
            n = 0L - n;
        }
        return new Decimal(s, DecimalDigits.longToDigits(n), 0);
    }

    static Decimal fromString(String text) {
        if (text == null) {
            throw new DecimalException("null decimal");
        }
        String s = text.trim();
        if (s.length() == 0) {
            throw new DecimalException("empty decimal");
        }
        int sign = 1;
        int i = 0;
        if (s.charAt(0) == 43) {
            i = 1;
        } else {
            if (s.charAt(0) == 45) {
                sign = -1;
                i = 1;
            }
        }
        if (i >= s.length()) {
            throw new DecimalException("bad decimal: " + text);
        }
        int dot = -1;
        String digs = "";
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c == 46) {
                if (dot >= 0) {
                    throw new DecimalException("bad decimal: " + text);
                }
                dot = digs.length();
            } else {
                if (c < 48 || c > 57) {
                    throw new DecimalException("bad decimal: " + text);
                }
                digs = digs + DecimalDigits.ch(c);
            }
            i = i + 1;
        }
        if (digs.length() == 0) {
            digs = "0";
        }
        int sc = 0;
        if (dot >= 0) {
            sc = digs.length() - dot;
        }
        return new Decimal(sign, digs, sc).stripTrailingZeros();
    }

    Decimal stripTrailingZeros() {
        if (scale == 0) {
            return this;
        }
        String d = digits;
        int sc = scale;
        while (sc > 0 && d.length() > 0 && d.charAt(d.length() - 1) == 48) {
            d = d.substring(0, d.length() - 1);
            sc = sc - 1;
        }
        if (d.length() == 0) {
            d = "0";
            sc = 0;
        }
        return new Decimal(sign, d, sc);
    }

    int isZero() {
        if (digits.equals("0")) {
            return 1;
        }
        return 0;
    }

    Decimal negate() {
        if (isZero() != 0) {
            return this;
        }
        return new Decimal(0 - sign, digits, scale);
    }

    Decimal abs() {
        return new Decimal(1, digits, scale);
    }

    int compareAbs(Decimal o) {
        Decimal a = alignScale(this, o);
        Decimal b = alignScale(o, this);
        return DecimalDigits.compare(a.digits, b.digits);
    }

    int compareTo(Decimal o) {
        if (sign != o.sign) {
            if (isZero() != 0 && o.isZero() != 0) {
                return 0;
            }
            if (sign < 0) {
                return -1;
            }
            return 1;
        }
        int c = compareAbs(o);
        if (sign < 0) {
            return 0 - c;
        }
        return c;
    }

    static Decimal alignScale(Decimal x, Decimal y) {
        if (x.scale >= y.scale) {
            return x;
        }
        int add = y.scale - x.scale;
        return new Decimal(x.sign, DecimalDigits.mulPow10(x.digits, add), y.scale);
    }

    Decimal add(Decimal o) {
        Decimal a = alignScale(this, o);
        Decimal b = alignScale(o, this);
        int sc = a.scale;
        if (a.sign == b.sign) {
            return new Decimal(a.sign, DecimalDigits.add(a.digits, b.digits), sc).stripTrailingZeros();
        }
        int cmp = DecimalDigits.compare(a.digits, b.digits);
        if (cmp == 0) {
            return ZERO;
        }
        if (cmp > 0) {
            return new Decimal(a.sign, DecimalDigits.sub(a.digits, b.digits), sc).stripTrailingZeros();
        }
        return new Decimal(b.sign, DecimalDigits.sub(b.digits, a.digits), sc).stripTrailingZeros();
    }

    Decimal sub(Decimal o) {
        return add(o.negate());
    }

    Decimal mul(Decimal o) {
        if (isZero() != 0 || o.isZero() != 0) {
            return ZERO;
        }
        int s = sign * o.sign;
        String d = DecimalDigits.mul(digits, o.digits);
        int sc = scale + o.scale;
        return new Decimal(s, d, sc).stripTrailingZeros();
    }

    Decimal div(Decimal o) {
        return divScale(o, DEFAULT_DIV_SCALE);
    }

    Decimal divScale(Decimal o, int outScale) {
        if (o.isZero() != 0) {
            throw new DecimalException("division by zero");
        }
        if (isZero() != 0) {
            return ZERO;
        }
        int s = sign * o.sign;
        int want = outScale;
        if (want < 0) {
            want = 0;
        }
        /* (a / 10^sa) / (b / 10^sb) = (a * 10^(want+sb)) / (b * 10^sa)  then scale=want */
        String num = DecimalDigits.mulPow10(digits, want + o.scale);
        String den = DecimalDigits.mulPow10(o.digits, scale);
        String q = DecimalDigits.divRoundHalfUp(num, den);
        return new Decimal(s, q, want).stripTrailingZeros();
    }

    Decimal percent() {
        return div(Decimal.fromLong(100L));
    }

    Decimal sqrt() {
        if (sign < 0) {
            throw new DecimalException("square root of negative");
        }
        if (isZero() != 0) {
            return ZERO;
        }
        /* Newton on Decimal with fixed scale */
        int sc = DEFAULT_DIV_SCALE;
        Decimal x = this;
        if (compareTo(ONE) < 0) {
            x = ONE;
        }
        int i = 0;
        while (i < 64) {
            Decimal nx = x.add(divScale(x, sc)).div(Decimal.fromLong(2L));
            if (nx.compareTo(x) == 0) {
                return nx.stripTrailingZeros();
            }
            x = nx;
            i = i + 1;
        }
        return x.stripTrailingZeros();
    }

    Decimal square() {
        return mul(this);
    }

    Decimal reciprocal() {
        return ONE.div(this);
    }

    double toDouble() {
        return DecimalDigits.parseDouble(toPlainString());
    }

    static Decimal fromDouble(double v) {
        /* Via string to avoid binary float surprises for finite values used by scientific UI. */
        return fromString(DecimalDigits.doubleToString(v));
    }

    String toPlainString() {
        String body = digits;
        if (scale == 0) {
            if (sign < 0) {
                return "-" + body;
            }
            return body;
        }
        while (body.length() <= scale) {
            body = "0" + body;
        }
        int cut = body.length() - scale;
        String out = body.substring(0, cut) + "." + body.substringFrom(cut);
        if (sign < 0) {
            return "-" + out;
        }
        return out;
    }

    String toDisplayString(int maxFrac) {
        Decimal t = this;
        if (scale > maxFrac) {
            t = roundToScale(maxFrac);
        }
        return t.toPlainString();
    }

    Decimal roundToScale(int newScale) {
        if (newScale < 0) {
            newScale = 0;
        }
        if (scale <= newScale) {
            return new Decimal(sign, DecimalDigits.mulPow10(digits, newScale - scale), newScale);
        }
        int drop = scale - newScale;
        String q = DecimalDigits.divRoundHalfUp(digits, DecimalDigits.mulPow10("1", drop));
        return new Decimal(sign, q, newScale).stripTrailingZeros();
    }
}

class DecimalDigits {
    static String ch(int c) {
        if (c == 48) {
            return "0";
        }
        if (c == 49) {
            return "1";
        }
        if (c == 50) {
            return "2";
        }
        if (c == 51) {
            return "3";
        }
        if (c == 52) {
            return "4";
        }
        if (c == 53) {
            return "5";
        }
        if (c == 54) {
            return "6";
        }
        if (c == 55) {
            return "7";
        }
        if (c == 56) {
            return "8";
        }
        return "9";
    }

    static String stripLeadingZeros(String d) {
        int i = 0;
        while (i + 1 < d.length() && d.charAt(i) == 48) {
            i = i + 1;
        }
        return d.substringFrom(i);
    }

    static String longToDigits(long n) {
        if (n == 0L) {
            return "0";
        }
        String out = "";
        long v = n;
        while (v > 0L) {
            long rem = v - (v / 10L) * 10L;
            int digit = 0;
            while (rem > 0L) {
                digit = digit + 1;
                rem = rem - 1L;
            }
            out = String.valueOf(digit) + out;
            v = v / 10L;
        }
        return out;
    }

    static int compare(String a, String b) {
        String x = stripLeadingZeros(a);
        String y = stripLeadingZeros(b);
        if (x.length() != y.length()) {
            if (x.length() > y.length()) {
                return 1;
            }
            return -1;
        }
        int i = 0;
        while (i < x.length()) {
            int ca = x.charAt(i);
            int cb = y.charAt(i);
            if (ca != cb) {
                if (ca > cb) {
                    return 1;
                }
                return -1;
            }
            i = i + 1;
        }
        return 0;
    }

    static String mulPow10(String d, int n) {
        if (n <= 0) {
            return stripLeadingZeros(d);
        }
        String out = stripLeadingZeros(d);
        int i = 0;
        while (i < n) {
            out = out + "0";
            i = i + 1;
        }
        return out;
    }

    static String add(String a, String b) {
        String x = stripLeadingZeros(a);
        String y = stripLeadingZeros(b);
        String out = "";
        int i = x.length() - 1;
        int j = y.length() - 1;
        int carry = 0;
        while (i >= 0 || j >= 0 || carry != 0) {
            int da = 0;
            int db = 0;
            if (i >= 0) {
                da = x.charAt(i) - 48;
                i = i - 1;
            }
            if (j >= 0) {
                db = y.charAt(j) - 48;
                j = j - 1;
            }
            int s = da + db + carry;
            carry = s / 10;
            int dig = s - carry * 10;
            out = String.valueOf(dig) + out;
        }
        return stripLeadingZeros(out);
    }

    static String sub(String a, String b) {
        /* assumes a >= b >= 0 */
        String x = stripLeadingZeros(a);
        String y = stripLeadingZeros(b);
        String out = "";
        int i = x.length() - 1;
        int j = y.length() - 1;
        int borrow = 0;
        while (i >= 0) {
            int da = x.charAt(i) - 48 - borrow;
            int db = 0;
            if (j >= 0) {
                db = y.charAt(j) - 48;
                j = j - 1;
            }
            if (da < db) {
                da = da + 10;
                borrow = 1;
            } else {
                borrow = 0;
            }
            int dig = da - db;
            out = String.valueOf(dig) + out;
            i = i - 1;
        }
        return stripLeadingZeros(out);
    }

    static String mul(String a, String b) {
        String x = stripLeadingZeros(a);
        String y = stripLeadingZeros(b);
        if (x.equals("0") || y.equals("0")) {
            return "0";
        }
        int n = x.length();
        int m = y.length();
        int[] acc = new int[n + m];
        int i = n - 1;
        while (i >= 0) {
            int da = x.charAt(i) - 48;
            int j = m - 1;
            while (j >= 0) {
                int db = y.charAt(j) - 48;
                int p = i + j + 1;
                int s = acc[p] + da * db;
                acc[p] = s - (s / 10) * 10;
                acc[p - 1] = acc[p - 1] + s / 10;
                j = j - 1;
            }
            i = i - 1;
        }
        String out = "";
        int k = 0;
        while (k < acc.length) {
            if (!(out.length() == 0 && acc[k] == 0)) {
                out = out + String.valueOf(acc[k]);
            }
            k = k + 1;
        }
        if (out.length() == 0) {
            return "0";
        }
        return out;
    }

    static String divRoundHalfUp(String num, String den) {
        String n = stripLeadingZeros(num);
        String d = stripLeadingZeros(den);
        if (d.equals("0")) {
            throw new DecimalException("division by zero");
        }
        if (n.equals("0")) {
            return "0";
        }
        /* long division; compute floor(n/d) and round from remainder*2 ? d */
        String q = "";
        String rem = "0";
        int i = 0;
        while (i < n.length()) {
            rem = stripLeadingZeros(rem + ch(n.charAt(i)));
            int digit = 0;
            while (compare(rem, d) >= 0) {
                rem = sub(rem, d);
                digit = digit + 1;
            }
            q = q + String.valueOf(digit);
            i = i + 1;
        }
        q = stripLeadingZeros(q);
        /* half-up: if rem*2 >= d then q++ */
        String twice = add(rem, rem);
        if (compare(twice, d) >= 0) {
            q = add(q, "1");
        }
        return q;
    }

    static double parseDouble(String s) {
        /* minimal parse for scientific bridge */
        return DoubleParse.parse(s);
    }

    static String doubleToString(double v) {
        return DoubleParse.format(v);
    }
}

/** Tiny double↔string helpers used only at Decimal scientific boundary.
 * @author 张晓达
 */
class DoubleParse {
    @Native(symbol = "jnative_double_to_string")
    static native NativePtr formatNative(double v);

    @Native(symbol = "jnative_string_to_double")
    static native double parseNative(String s);

    @Native(symbol = "jnative_crypto_free")
    static native void freeNative(NativePtr p);

    static String format(double v) {
        NativePtr p = formatNative(v);
        if (p == NativePtr.NULL) {
            return "0";
        }
        String s = NativeMemory.fromCString(p);
        freeNative(p);
        if (s == null || s.length() == 0) {
            return "0";
        }
        return s;
    }

    static double parse(String s) {
        if (s == null || s.length() == 0) {
            return 0.0;
        }
        return parseNative(s);
    }
}
