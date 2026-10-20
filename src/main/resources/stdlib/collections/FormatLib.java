// JNative string formatting: String.format / System.out.printf.
//
// The parser rewrites these calls to JnFormat.format / JnFormat.join (see Parser).
// Supported conversions: %d %s %S %f %x %X %o %c %b %B %n %%, argument indexes (%1$s), the flags
// '-' '0' '+' ' ' ',' '(' '#', a width and a precision. %f rounds HALF_UP on the shortest decimal
// representation of the double, exactly like java.util.Formatter. %e / %g are not supported.

final class JnFormat {
    static String format(String fmt, Object... args) {
        StringBuilder out = new StringBuilder();
        int n = fmt.length();
        int i = 0;
        int nextArg = 0;
        while (i < n) {
            char c = fmt.charAt(i);
            if (c != '%') {
                int j = i;
                while (j < n && fmt.charAt(j) != '%') {
                    j++;
                }
                out.append(fmt.substring(i, j));
                i = j;
                continue;
            }
            i++;
            if (i >= n) {
                throw new IllegalArgumentException("Format specifier '%' is incomplete");
            }
            // optional argument index: digits followed by '$'
            int argIndex = -1;
            int k = i;
            int num = 0;
            while (k < n && fmt.charAt(k) >= '0' && fmt.charAt(k) <= '9') {
                num = num * 10 + (fmt.charAt(k) - '0');
                k++;
            }
            if (k > i && k < n && fmt.charAt(k) == '$') {
                argIndex = num - 1;
                i = k + 1;
            }
            boolean left = false;
            boolean zero = false;
            boolean plus = false;
            boolean space = false;
            boolean group = false;
            boolean paren = false;
            boolean alt = false;
            boolean flagsDone = false;
            while (i < n && !flagsDone) {
                char f = fmt.charAt(i);
                if (f == '-') {
                    left = true;
                } else if (f == '0') {
                    zero = true;
                } else if (f == '+') {
                    plus = true;
                } else if (f == ' ') {
                    space = true;
                } else if (f == ',') {
                    group = true;
                } else if (f == '(') {
                    paren = true;
                } else if (f == '#') {
                    alt = true;
                } else {
                    flagsDone = true;
                    i--;
                }
                i++;
            }
            int width = -1;
            if (i < n && fmt.charAt(i) >= '1' && fmt.charAt(i) <= '9') {
                width = 0;
                while (i < n && fmt.charAt(i) >= '0' && fmt.charAt(i) <= '9') {
                    width = width * 10 + (fmt.charAt(i) - '0');
                    i++;
                }
            }
            int prec = -1;
            if (i < n && fmt.charAt(i) == '.') {
                i++;
                if (i >= n || fmt.charAt(i) < '0' || fmt.charAt(i) > '9') {
                    throw new IllegalArgumentException("Illegal precision in format string");
                }
                prec = 0;
                while (i < n && fmt.charAt(i) >= '0' && fmt.charAt(i) <= '9') {
                    prec = prec * 10 + (fmt.charAt(i) - '0');
                    i++;
                }
            }
            if (i >= n) {
                throw new IllegalArgumentException("Format specifier is incomplete: " + fmt);
            }
            char conv = fmt.charAt(i);
            i++;
            if (conv == 'n') {
                out.append("\n");
                continue;
            }
            if (conv == '%') {
                out.append(pad("%", width, left, false));
                continue;
            }
            int idx = argIndex >= 0 ? argIndex : nextArg++;
            if (idx >= args.length) {
                throw new IllegalArgumentException("Format specifier '%" + conv + "' has no matching argument");
            }
            Object arg = args[idx];
            String body;
            if (conv == 'd') {
                body = formatInteger(arg, plus, space, group, paren, zero, width, left, 'd', alt);
            } else if (conv == 'x' || conv == 'X' || conv == 'o') {
                body = formatInteger(arg, plus, space, group, paren, zero, width, left, conv, alt);
            } else if (conv == 'f') {
                body = formatFloat(arg, prec, plus, space, group, paren, zero, width, left);
            } else if (conv == 's' || conv == 'S') {
                String s = "" + arg;
                if (prec >= 0 && prec < s.length()) {
                    s = s.substring(0, prec);
                }
                if (conv == 'S') {
                    s = s.toUpperCase();
                }
                body = pad(s, width, left, false);
            } else if (conv == 'c') {
                String s;
                if (arg == null) {
                    s = "null";
                } else if (arg instanceof Character) {
                    s = String.valueOf(((Character) arg).charValue());
                } else if (arg instanceof Integer) {
                    s = String.valueOf((char) ((Integer) arg).intValue());
                } else {
                    throw new IllegalArgumentException("%c is not applicable to the argument type");
                }
                body = pad(s, width, left, false);
            } else if (conv == 'b' || conv == 'B') {
                String s;
                if (arg == null) {
                    s = "false";
                } else if (arg instanceof Boolean) {
                    s = ((Boolean) arg).booleanValue() ? "true" : "false";
                } else {
                    s = "true";
                }
                if (conv == 'B') {
                    s = s.toUpperCase();
                }
                body = pad(s, width, left, false);
            } else {
                throw new IllegalArgumentException("Unknown format conversion: %" + conv);
            }
            out.append(body);
        }
        return out.toString();
    }

    static String pad(String s, int width, boolean left, boolean zero) {
        int len = s.length();
        if (width <= len) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        if (left) {
            sb.append(s);
            for (int i = len; i < width; i++) {
                sb.append(" ");
            }
        } else {
            for (int i = len; i < width; i++) {
                sb.append(zero ? "0" : " ");
            }
            sb.append(s);
        }
        return sb.toString();
    }

    /** Inserts ',' every three digits of an unsigned digit string. */
    static String groupDigits(String digits) {
        int len = digits.length();
        if (len <= 3) {
            return digits;
        }
        StringBuilder sb = new StringBuilder();
        int first = len % 3;
        if (first > 0) {
            sb.append(digits.substring(0, first));
        }
        for (int p = first; p < len; p += 3) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(digits.substring(p, p + 3));
        }
        return sb.toString();
    }

    /** Applies sign flags, zero padding and width to an unsigned magnitude string. */
    static String finishNumber(String magnitude, boolean negative, boolean plus, boolean space,
                               boolean paren, boolean zero, int width, boolean left, String prefix) {
        String lead = "";
        String trail = "";
        if (negative) {
            if (paren) {
                lead = "(";
                trail = ")";
            } else {
                lead = "-";
            }
        } else if (plus) {
            lead = "+";
        } else if (space) {
            lead = " ";
        }
        lead = lead + prefix;
        if (zero && !left && width > 0) {
            int fill = width - lead.length() - magnitude.length() - trail.length();
            StringBuilder z = new StringBuilder();
            for (int i = 0; i < fill; i++) {
                z.append("0");
            }
            magnitude = z.toString() + magnitude;
        }
        return pad(lead + magnitude + trail, width, left, false);
    }

    static String formatInteger(Object arg, boolean plus, boolean space, boolean group, boolean paren,
                                boolean zero, int width, boolean left, char conv, boolean alt) {
        if (arg == null) {
            return pad("null", width, left, false);
        }
        boolean isInt = arg instanceof Integer;
        long v;
        if (isInt) {
            v = ((Integer) arg).longValue();
        } else if (arg instanceof Long) {
            v = ((Long) arg).longValue();
        } else {
            throw new IllegalArgumentException("%" + conv + " is not applicable to the argument type");
        }
        if (conv == 'd') {
            boolean neg = v < 0;
            String s = Long.toString(v);
            if (neg) {
                s = s.substring(1);
            }
            if (group) {
                s = groupDigits(s);
            }
            return finishNumber(s, neg, plus, space, paren, zero, width, left, "");
        }
        String digits;
        String prefix = "";
        if (conv == 'o') {
            digits = isInt ? Integer.toOctalString((int) v) : Long.toOctalString(v);
            if (alt) {
                prefix = "0";
            }
        } else {
            digits = isInt ? Integer.toHexString((int) v) : Long.toHexString(v);
            if (alt) {
                prefix = "0x";
            }
        }
        if (conv == 'X') {
            digits = digits.toUpperCase();
            prefix = prefix.toUpperCase();
        }
        return finishNumber(digits, false, false, false, false, zero, width, left, prefix);
    }

    static String formatFloat(Object arg, int prec, boolean plus, boolean space, boolean group,
                              boolean paren, boolean zero, int width, boolean left) {
        if (arg == null) {
            return pad("null", width, left, false);
        }
        double v;
        if (arg instanceof Double) {
            v = ((Double) arg).doubleValue();
        } else if (arg instanceof Float) {
            v = ((Float) arg).doubleValue();
        } else {
            throw new IllegalArgumentException("%f is not applicable to the argument type");
        }
        if (v != v) {
            return pad("NaN", width, left, false);
        }
        if (v == 1.0 / 0.0 || v == -1.0 / 0.0) {
            boolean neg = v < 0;
            String s = "Infinity";
            if (neg) {
                s = paren ? "(Infinity)" : "-Infinity";
            } else if (plus) {
                s = "+Infinity";
            } else if (space) {
                s = " Infinity";
            }
            return pad(s, width, left, false);
        }
        if (prec < 0) {
            prec = 6;
        }
        boolean neg = v < 0 || (v == 0.0 && 1.0 / v < 0);
        double a = neg ? -v : v;
        String fixed = fixedDecimal(a, prec);
        int dot = fixed.indexOf('.');
        String intPart = dot < 0 ? fixed : fixed.substring(0, dot);
        String fracPart = dot < 0 ? "" : fixed.substring(dot);
        if (group) {
            intPart = groupDigits(intPart);
        }
        return finishNumber(intPart + fracPart, neg, plus, space, paren, zero, width, left, "");
    }

    /**
     * Plain decimal text of a non-negative finite double with exactly {@code prec} fraction digits,
     * rounded HALF_UP from the shortest round-trip representation (Double.toString digits).
     */
    static String fixedDecimal(double a, int prec) {
        String s = "" + a;
        int e = s.indexOf('E');
        int exp = 0;
        String mant = s;
        if (e >= 0) {
            mant = s.substring(0, e);
            exp = Integer.parseInt(s.substring(e + 1));
        }
        int dot = mant.indexOf('.');
        String digits;
        int pointPos;
        if (dot < 0) {
            digits = mant;
            pointPos = mant.length();
        } else {
            digits = mant.substring(0, dot) + mant.substring(dot + 1);
            pointPos = dot;
        }
        pointPos += exp;
        // strip leading zeros of the digit string (they only shift the decimal point)
        int lead = 0;
        while (lead < digits.length() - 1 && digits.charAt(lead) == '0') {
            lead++;
        }
        digits = digits.substring(lead);
        pointPos -= lead;
        int n = digits.length();
        int cut = pointPos + prec; // digits to keep, counted from the first significant digit
        String scaled; // value * 10^prec as an integer, before rounding
        boolean roundUp = false;
        if (cut < 0) {
            scaled = "0";
        } else if (cut >= n) {
            StringBuilder sb = new StringBuilder(digits);
            for (int i = n; i < cut; i++) {
                sb.append("0");
            }
            scaled = sb.toString();
        } else {
            scaled = cut == 0 ? "0" : digits.substring(0, cut);
            roundUp = digits.charAt(cut) >= '5';
        }
        if (roundUp) {
            scaled = incrementDecimal(scaled);
        }
        // strip leading zeros, then re-pad so there is at least one integer digit
        int z = 0;
        while (z < scaled.length() - 1 && scaled.charAt(z) == '0') {
            z++;
        }
        scaled = scaled.substring(z);
        StringBuilder sb = new StringBuilder();
        for (int i = scaled.length(); i < prec + 1; i++) {
            sb.append("0");
        }
        sb.append(scaled);
        String all = sb.toString();
        int len = all.length();
        if (prec == 0) {
            return all;
        }
        return all.substring(0, len - prec) + "." + all.substring(len - prec);
    }

    static String incrementDecimal(String s) {
        char[] cs = s.toCharArray();
        int i = cs.length - 1;
        while (i >= 0) {
            if (cs[i] == '9') {
                cs[i] = '0';
                i--;
            } else {
                cs[i] = (char) (cs[i] + 1);
                return String.valueOf(cs);
            }
        }
        return "1" + String.valueOf(cs);
    }
}
