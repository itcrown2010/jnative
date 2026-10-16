/**
 * JNative Windows Calculator — arbitrary-precision decimal-float number.
 *
 * value = sign * int(digits) * 10^exp, digits without leading/trailing zeros.
 * Internal precision is 40 significant digits (Windows Calculator displays up to 32),
 * decimal exponent range is +/-9999 like the in-box app.
 *
 * Built only on the stdlib DecimalDigits string primitives (no BigDecimal).
 *
 * @author 张晓达
 */
class WcException extends RuntimeException {
    int kind;

    WcException(int k, String msg) {
        super(msg);
        kind = k;
    }
}

class WcErr {
    static final int DIV_ZERO = 1;
    static final int UNDEFINED = 2;
    static final int INVALID = 3;
    static final int OVERFLOW = 4;

    static String message(int kind) {
        if (kind == DIV_ZERO) {
            return "Cannot divide by zero";
        }
        if (kind == UNDEFINED) {
            return "Result is undefined";
        }
        if (kind == OVERFLOW) {
            return "Overflow";
        }
        return "Invalid input";
    }

    static WcException make(int kind) {
        return new WcException(kind, message(kind));
    }
}

class WcDigits {
    /** Integer quotient of two decimal digit strings, rounded half-up (int-array long division). */
    static String divRound(String num, String den) {
        String n = DecimalDigits.stripLeadingZeros(num);
        String d = DecimalDigits.stripLeadingZeros(den);
        if (d.equals("0")) {
            throw WcErr.make(WcErr.DIV_ZERO);
        }
        if (n.equals("0")) {
            return "0";
        }
        int dl = d.length();
        int nl = n.length();
        int[] dd = new int[dl];
        int k = 0;
        while (k < dl) {
            dd[k] = d.charAt(k) - 48;
            k = k + 1;
        }
        int[] rem = new int[dl + 1];
        int[] qd = new int[nl];
        int i = 0;
        while (i < nl) {
            k = 0;
            while (k < dl) {
                rem[k] = rem[k + 1];
                k = k + 1;
            }
            rem[dl] = n.charAt(i) - 48;
            int q = 0;
            while (geq(rem, dd, dl) != 0) {
                subtract(rem, dd, dl);
                q = q + 1;
            }
            qd[i] = q;
            i = i + 1;
        }
        /* round half-up: 2*rem >= d */
        int[] twice = new int[dl + 1];
        int carry = 0;
        k = dl;
        while (k >= 0) {
            int v = rem[k] * 2 + carry;
            if (v >= 10) {
                twice[k] = v - 10;
                carry = 1;
            } else {
                twice[k] = v;
                carry = 0;
            }
            k = k - 1;
        }
        if (geq(twice, dd, dl) != 0) {
            k = nl - 1;
            while (k >= 0) {
                if (qd[k] == 9) {
                    qd[k] = 0;
                    k = k - 1;
                } else {
                    qd[k] = qd[k] + 1;
                    break;
                }
            }
            if (k < 0) {
                String big = "1";
                int z = 0;
                while (z < nl) {
                    big = big + "0";
                    z = z + 1;
                }
                return big;
            }
        }
        String out = "";
        k = 0;
        while (k < nl) {
            if (!(out.length() == 0 && qd[k] == 0)) {
                out = out + WcFmt.digitChar(qd[k]);
            }
            k = k + 1;
        }
        if (out.length() == 0) {
            return "0";
        }
        return out;
    }

    /** rem (dl+1 digits, big-endian) >= dd (dl digits)? */
    static int geq(int[] rem, int[] dd, int dl) {
        int k = 0;
        while (k <= dl) {
            int a = rem[k];
            int b = 0;
            if (k > 0) {
                b = dd[k - 1];
            }
            if (a != b) {
                if (a > b) {
                    return 1;
                }
                return 0;
            }
            k = k + 1;
        }
        return 1;
    }

    static void subtract(int[] rem, int[] dd, int dl) {
        int borrow = 0;
        int k = dl;
        while (k >= 0) {
            int b = 0;
            if (k > 0) {
                b = dd[k - 1];
            }
            int v = rem[k] - b - borrow;
            if (v < 0) {
                v = v + 10;
                borrow = 1;
            } else {
                borrow = 0;
            }
            rem[k] = v;
            k = k - 1;
        }
    }
}

class WcNum {
    /* final: inlined at compile time, so WcMath constants built in its static init
     * do not depend on WcNum class-init order. */
    static final int SIG = 40;
    static final int MAX_EXP = 9999;

    int sign;
    String digits;
    int exp;

    static WcNum ZERO = new WcNum(0, "0", 0);
    static WcNum ONE = new WcNum(1, "1", 0);

    WcNum(int s, String d, int e) {
        int sg = s;
        String ds = DecimalDigits.stripLeadingZeros(d);
        int ex = e;
        if (ds.equals("0") || sg == 0) {
            sign = 0;
            digits = "0";
            exp = 0;
            return;
        }
        if (ds.length() > SIG) {
            int drop = ds.length() - SIG;
            String keep = ds.substring(0, SIG);
            int next = ds.charAt(SIG) - 48;
            ex = ex + drop;
            ds = keep;
            if (next >= 5) {
                ds = DecimalDigits.add(ds, "1");
            }
        }
        /* strip trailing zeros */
        int end = ds.length();
        while (end > 1 && ds.charAt(end - 1) == 48) {
            end = end - 1;
            ex = ex + 1;
        }
        ds = ds.substring(0, end);
        if (sg < 0) {
            sign = -1;
        } else {
            sign = 1;
        }
        digits = ds;
        exp = ex;
    }

    static WcNum fromLong(long v) {
        if (v == 0L) {
            return ZERO;
        }
        String s = "" + v;
        if (s.charAt(0) == 45) {
            return new WcNum(-1, s.substringFrom(1), 0);
        }
        return new WcNum(1, s, 0);
    }

    static WcNum fromInt(int v) {
        return fromLong((long) v);
    }

    /** Parse "-12.50", "3.e+4", "1e-5". Returns null if malformed. */
    static WcNum parse(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        int n = s.length();
        if (n == 0) {
            return null;
        }
        int i = 0;
        int sg = 1;
        if (s.charAt(0) == 45) {
            sg = -1;
            i = 1;
        } else {
            if (s.charAt(0) == 43) {
                i = 1;
            }
        }
        String ds = "";
        int fracDigits = 0;
        int seenDot = 0;
        int any = 0;
        while (i < n) {
            int c = s.charAt(i);
            if (c >= 48 && c <= 57) {
                ds = ds + s.substring(i, i + 1);
                any = 1;
                if (seenDot != 0) {
                    fracDigits = fracDigits + 1;
                }
            } else {
                if (c == 46 && seenDot == 0) {
                    seenDot = 1;
                } else {
                    break;
                }
            }
            i = i + 1;
        }
        if (any == 0) {
            return null;
        }
        int ex = 0;
        if (i < n) {
            int c2 = s.charAt(i);
            if (c2 != 101 && c2 != 69) {
                return null;
            }
            i = i + 1;
            int esg = 1;
            if (i < n && s.charAt(i) == 45) {
                esg = -1;
                i = i + 1;
            } else {
                if (i < n && s.charAt(i) == 43) {
                    i = i + 1;
                }
            }
            int ev = 0;
            int eany = 0;
            while (i < n) {
                int c3 = s.charAt(i);
                if (c3 < 48 || c3 > 57) {
                    return null;
                }
                if (ev < 100000) {
                    ev = ev * 10 + (c3 - 48);
                }
                eany = 1;
                i = i + 1;
            }
            if (eany == 0) {
                ev = 0;
            }
            ex = esg * ev;
        }
        return new WcNum(sg, ds, ex - fracDigits);
    }

    static WcNum fromDouble(double v) {
        if (v != v) {
            throw WcErr.make(WcErr.INVALID);
        }
        if (v > 1.7e308 || v < -1.7e308) {
            throw WcErr.make(WcErr.OVERFLOW);
        }
        if (v == 0.0) {
            return ZERO;
        }
        String s = DoubleParse.format(v);
        WcNum r = parse(s);
        if (r == null) {
            throw WcErr.make(WcErr.INVALID);
        }
        return r;
    }

    static WcNum pow10(int n) {
        return new WcNum(1, "1", n);
    }

    int isZero() {
        if (sign == 0) {
            return 1;
        }
        return 0;
    }

    /** Exponent of the leading digit (scientific exponent). */
    int exp10() {
        return digits.length() - 1 + exp;
    }

    int isInteger() {
        if (sign == 0 || exp >= 0) {
            return 1;
        }
        return 0;
    }

    WcNum negate() {
        if (sign == 0) {
            return this;
        }
        return new WcNum(0 - sign, digits, exp);
    }

    WcNum abs() {
        if (sign >= 0) {
            return this;
        }
        return new WcNum(1, digits, exp);
    }

    WcNum shiftExp(int k) {
        if (sign == 0) {
            return this;
        }
        return new WcNum(sign, digits, exp + k);
    }

    /** Round half-up to n significant digits. */
    WcNum roundSig(int n) {
        if (sign == 0 || digits.length() <= n) {
            return this;
        }
        int drop = digits.length() - n;
        String keep = digits.substring(0, n);
        int next = digits.charAt(n) - 48;
        if (next >= 5) {
            keep = DecimalDigits.add(keep, "1");
        }
        return new WcNum(sign, keep, exp + drop);
    }

    /** Magnitude compare: -1, 0, 1. */
    int cmpAbs(WcNum o) {
        if (sign == 0 && o.sign == 0) {
            return 0;
        }
        if (sign == 0) {
            return -1;
        }
        if (o.sign == 0) {
            return 1;
        }
        int ea = exp10();
        int eb = o.exp10();
        if (ea != eb) {
            if (ea > eb) {
                return 1;
            }
            return -1;
        }
        String a = digits;
        String b = o.digits;
        while (a.length() < b.length()) {
            a = a + "0";
        }
        while (b.length() < a.length()) {
            b = b + "0";
        }
        return DecimalDigits.compare(a, b);
    }

    int cmp(WcNum o) {
        if (sign != o.sign) {
            if (sign < o.sign) {
                return -1;
            }
            return 1;
        }
        if (sign == 0) {
            return 0;
        }
        int c = cmpAbs(o);
        if (sign < 0) {
            return 0 - c;
        }
        return c;
    }

    int equalsNum(WcNum o) {
        if (cmp(o) == 0) {
            return 1;
        }
        return 0;
    }

    WcNum checkRange() {
        if (sign == 0) {
            return this;
        }
        int e = exp10();
        if (e > MAX_EXP) {
            throw WcErr.make(WcErr.OVERFLOW);
        }
        if (e < 0 - MAX_EXP) {
            return ZERO;
        }
        return this;
    }

    WcNum add(WcNum o) {
        if (sign == 0) {
            return o;
        }
        if (o.sign == 0) {
            return this;
        }
        int ea = exp10();
        int eb = o.exp10();
        if (ea - eb > SIG + 2) {
            return this;
        }
        if (eb - ea > SIG + 2) {
            return o;
        }
        int e = exp;
        if (o.exp < e) {
            e = o.exp;
        }
        String a = DecimalDigits.mulPow10(digits, exp - e);
        String b = DecimalDigits.mulPow10(o.digits, o.exp - e);
        if (sign == o.sign) {
            return new WcNum(sign, DecimalDigits.add(a, b), e).checkRange();
        }
        int c = DecimalDigits.compare(a, b);
        if (c == 0) {
            return ZERO;
        }
        if (c > 0) {
            return new WcNum(sign, DecimalDigits.sub(a, b), e).checkRange();
        }
        return new WcNum(o.sign, DecimalDigits.sub(b, a), e).checkRange();
    }

    WcNum sub(WcNum o) {
        return add(o.negate());
    }

    WcNum mul(WcNum o) {
        if (sign == 0 || o.sign == 0) {
            return ZERO;
        }
        return new WcNum(sign * o.sign, DecimalDigits.mul(digits, o.digits), exp + o.exp).checkRange();
    }

    /** Division; caller must reject zero divisor first (throws DIV_ZERO/UNDEFINED). */
    WcNum div(WcNum o) {
        if (o.sign == 0) {
            if (sign == 0) {
                throw WcErr.make(WcErr.UNDEFINED);
            }
            throw WcErr.make(WcErr.DIV_ZERO);
        }
        if (sign == 0) {
            return ZERO;
        }
        int k = SIG + 2 + o.digits.length() - digits.length();
        if (k < 0) {
            k = 0;
        }
        String num = DecimalDigits.mulPow10(digits, k);
        String q = WcDigits.divRound(num, o.digits);
        return new WcNum(sign * o.sign, q, exp - o.exp - k).checkRange();
    }

    /** Truncate toward zero to an integer value. */
    WcNum truncate() {
        if (sign == 0 || exp >= 0) {
            return this;
        }
        int cut = digits.length() + exp;
        if (cut <= 0) {
            return ZERO;
        }
        return new WcNum(sign, digits.substring(0, cut), 0);
    }

    WcNum floorNum() {
        WcNum t = truncate();
        if (sign < 0 && t.cmp(this) != 0) {
            return t.sub(ONE);
        }
        return t;
    }

    WcNum ceilNum() {
        WcNum t = truncate();
        if (sign > 0 && t.cmp(this) != 0) {
            return t.add(ONE);
        }
        return t;
    }

    /** Truncated remainder (sign follows dividend). */
    WcNum rem(WcNum o) {
        if (o.sign == 0) {
            throw WcErr.make(WcErr.DIV_ZERO);
        }
        if (sign == 0) {
            return ZERO;
        }
        if (cmpAbs(o) < 0) {
            return this;
        }
        if (exp10() - o.exp10() > SIG) {
            return ZERO;
        }
        WcNum q = div(o).truncate();
        return sub(q.mul(o));
    }

    /** Exact-ish integer power (|n| bounded), rounded to SIG digits per step. */
    WcNum powInt(long n) {
        if (n == 0L) {
            return ONE;
        }
        long m = n;
        if (m < 0L) {
            m = 0L - m;
        }
        WcNum result = ONE;
        WcNum base = this;
        while (m > 0L) {
            if ((m & 1L) != 0L) {
                result = result.mul(base);
            }
            m = m >> 1;
            if (m > 0L) {
                base = base.mul(base);
            }
        }
        if (n < 0L) {
            return ONE.div(result);
        }
        return result;
    }

    /** Square root, 34 digits (double seed + Newton). Caller ensures sign >= 0. */
    WcNum sqrtNum() {
        if (sign == 0) {
            return ZERO;
        }
        int e10 = exp10();
        int k = e10 / 2;
        if (e10 < 0 && (e10 % 2) != 0) {
            k = k - 1;
        }
        WcNum d = shiftExp(0 - 2 * k);
        double seed = Math.sqrt(d.toDouble());
        WcNum y = fromDouble(seed);
        WcNum two = fromLong(2L);
        int i = 0;
        while (i < 3) {
            y = y.add(d.div(y)).div(two);
            i = i + 1;
        }
        return y.shiftExp(k);
    }

    double toDouble() {
        if (sign == 0) {
            return 0.0;
        }
        String s = digits + "e" + exp;
        if (sign < 0) {
            s = "-" + s;
        }
        return DoubleParse.parse(s);
    }

    /** Fits signed 64-bit integer value (after truncation check by caller). */
    int fitsLong() {
        if (sign == 0) {
            return 1;
        }
        if (exp < 0) {
            return 0;
        }
        int e10 = exp10();
        if (e10 > 18) {
            if (e10 > 19) {
                return 0;
            }
            String full = DecimalDigits.mulPow10(digits, exp);
            if (full.length() > 19) {
                return 0;
            }
            int c = DecimalDigits.compare(full, "9223372036854775807");
            if (c <= 0) {
                return 1;
            }
            if (sign < 0 && DecimalDigits.compare(full, "9223372036854775808") == 0) {
                return 1;
            }
            return 0;
        }
        return 1;
    }

    long toLong() {
        if (sign == 0) {
            return 0L;
        }
        String full = DecimalDigits.mulPow10(digits, exp);
        if (sign < 0 && full.equals("9223372036854775808")) {
            return 0L - 9223372036854775807L - 1L;
        }
        long r = 0L;
        int i = 0;
        while (i < full.length()) {
            r = r * 10L + (long) (full.charAt(i) - 48);
            i = i + 1;
        }
        if (sign < 0) {
            return 0L - r;
        }
        return r;
    }

    /** Plain positional decimal string (no grouping, no exponent). */
    String toPlain() {
        if (sign == 0) {
            return "0";
        }
        String body;
        if (exp >= 0) {
            body = DecimalDigits.mulPow10(digits, exp);
        } else {
            int frac = 0 - exp;
            String d = digits;
            while (d.length() <= frac) {
                d = "0" + d;
            }
            int cut = d.length() - frac;
            body = d.substring(0, cut) + "." + d.substringFrom(cut);
        }
        if (sign < 0) {
            return "-" + body;
        }
        return body;
    }
}
