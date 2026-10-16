/**
 * Display formatting for the JNative Windows Calculator (en-US separators).
 *
 * @author 张晓达
 */
class WcFmt {
    static String zeros(int n) {
        String s = "";
        int i = 0;
        while (i < n) {
            s = s + "0";
            i = i + 1;
        }
        return s;
    }

    /** Insert thousands separators into an unsigned digit string. */
    static String groupInt(String d, String sep) {
        int n = d.length();
        if (n <= 3) {
            return d;
        }
        String out = "";
        int first = n % 3;
        if (first == 0) {
            first = 3;
        }
        out = d.substring(0, first);
        int i = first;
        while (i < n) {
            out = out + sep + d.substring(i, i + 3);
            i = i + 3;
        }
        return out;
    }

    /** Group every `size` chars from the right (programmer bin/hex/oct), separator ' '. */
    static String groupRight(String d, int size) {
        int n = d.length();
        if (n <= size) {
            return d;
        }
        String out = "";
        int first = n % size;
        if (first == 0) {
            first = size;
        }
        out = d.substring(0, first);
        int i = first;
        while (i < n) {
            out = out + " " + d.substring(i, i + size);
            i = i + size;
        }
        return out;
    }

    /**
     * Format a value like Windows Calculator: at most maxSig significant digits,
     * scientific when the exponent is out of range or forceSci is set.
     */
    static String format(WcNum v, int maxSig, int group, int forceSci) {
        if (v.sign == 0) {
            if (forceSci != 0) {
                return "0e+0";
            }
            return "0";
        }
        WcNum r = v.roundSig(maxSig);
        int e10 = r.exp10();
        String digs = r.digits;
        String sg = "";
        if (r.sign < 0) {
            sg = "-";
        }
        if (forceSci != 0 || e10 >= maxSig || e10 < -6) {
            String mant = digs.substring(0, 1);
            if (digs.length() > 1) {
                mant = mant + "." + digs.substringFrom(1);
            }
            String es = "+";
            int ea = e10;
            if (e10 < 0) {
                es = "-";
                ea = 0 - e10;
            }
            return sg + mant + "e" + es + ea;
        }
        String ip;
        String fp;
        if (e10 >= 0) {
            if (digs.length() <= e10 + 1) {
                ip = digs + zeros(e10 + 1 - digs.length());
                fp = "";
            } else {
                ip = digs.substring(0, e10 + 1);
                fp = digs.substringFrom(e10 + 1);
            }
        } else {
            ip = "0";
            fp = zeros(0 - e10 - 1) + digs;
        }
        if (group != 0) {
            ip = groupInt(ip, ",");
        }
        if (fp.length() > 0) {
            return sg + ip + "." + fp;
        }
        return sg + ip;
    }

    /** Group the integer part of a typed entry ("-1234.50", "3.e+4"). */
    static String groupEntry(String text) {
        String s = text;
        String sg = "";
        if (s.length() > 0 && s.charAt(0) == 45) {
            sg = "-";
            s = s.substringFrom(1);
        }
        int cut = s.length();
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c == 46 || c == 101 || c == 69) {
                cut = i;
                break;
            }
            i = i + 1;
        }
        String ip = s.substring(0, cut);
        String rest = s.substringFrom(cut);
        if (ip.length() == 0) {
            ip = "0";
        }
        return sg + groupInt(ip, ",") + rest;
    }

    static String digitChar(int d) {
        return "0123456789ABCDEF".substring(d, d + 1);
    }

    /** Unsigned digits of v (masked to bits) in the given base. */
    static String baseDigits(long v, int base, int bits) {
        long u = v;
        if (bits < 64) {
            long m = (1L << bits) - 1L;
            u = u & m;
        }
        if (u == 0L) {
            return "0";
        }
        String out = "";
        if (base == 10) {
            /* signed decimal handled by caller */
            return "" + v;
        }
        int shift = 4;
        long mask = 15L;
        if (base == 2) {
            shift = 1;
            mask = 1L;
        } else {
            if (base == 8) {
                shift = 3;
                mask = 7L;
            }
        }
        int guard = 0;
        while (u != 0L && guard < 70) {
            int d = (int) (u & mask);
            out = digitChar(d) + out;
            u = u >>> shift;
            guard = guard + 1;
        }
        return out;
    }

    /** Programmer-mode display text for a value in a base. */
    static String progText(long v, int base, int bits, int group) {
        if (base == 10) {
            String s = "" + v;
            if (group == 0) {
                return s;
            }
            String sg = "";
            if (v < 0L) {
                sg = "-";
                s = s.substringFrom(1);
            }
            return sg + groupInt(s, ",");
        }
        String d = baseDigits(v, base, bits);
        if (group == 0) {
            return d;
        }
        if (base == 2) {
            return groupRight(d, 4);
        }
        if (base == 16) {
            return groupRight(d, 4);
        }
        return groupRight(d, 3);
    }
}
