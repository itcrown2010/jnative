/**
 * Small string / number helpers shared by the designer.
 *
 * @author 张晓达
 */
public class DsUtil {
    static boolean startsAt(String s, String prefix, int at) {
        if (at < 0 || at + prefix.length() > s.length()) {
            return false;
        }
        return s.substring(at, at + prefix.length()).equals(prefix);
    }

    static int parseInt(String s, int def) {
        if (s == null || s.length() == 0) {
            return def;
        }
        int i = 0;
        int sign = 1;
        if (s.charAt(0) == '-') {
            sign = -1;
            i = 1;
        }
        if (i >= s.length()) {
            return def;
        }
        long v = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return def;
            }
            v = v * 10 + (c - '0');
            if (v > 2000000000L) {
                return def;
            }
            i = i + 1;
        }
        return (int) (v * sign);
    }

    static double parseDouble(String s, double def) {
        if (s == null || s.length() == 0) {
            return def;
        }
        int i = 0;
        double sign = 1;
        if (s.charAt(0) == '-') {
            sign = -1;
            i = 1;
        }
        double v = 0;
        double frac = 0;
        double scale = 1;
        int dot = 0;
        int digits = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '.' && dot == 0) {
                dot = 1;
            } else if (c >= '0' && c <= '9') {
                digits = digits + 1;
                if (dot == 0) {
                    v = v * 10 + (c - '0');
                } else {
                    scale = scale / 10;
                    frac = frac + (c - '0') * scale;
                }
            } else {
                return def;
            }
            i = i + 1;
        }
        if (digits == 0) {
            return def;
        }
        return sign * (v + frac);
    }

    static String fmtDouble(double d) {
        if (d == Math.floor(d) && Math.abs(d) < 1000000000.0) {
            return "" + (long) d + ".0";
        }
        long scaled = Math.round(d * 1000.0);
        long ip = scaled / 1000;
        long fp = Math.abs(scaled % 1000);
        String f = "" + fp;
        while (f.length() < 3) {
            f = "0" + f;
        }
        while (f.length() > 1 && f.endsWith("0")) {
            f = f.substring(0, f.length() - 1);
        }
        String sign = (scaled < 0 && ip == 0) ? "-" : "";
        return sign + ip + "." + f;
    }

    /** Escape a string for a Java source literal. */
    static String javaString(String s) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '"') {
                sb.append("\\\"");
            } else if (c == '\\') {
                sb.append("\\\\");
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\t') {
                sb.append("\\t");
            } else if (c == '\r') {
                sb.append("\\r");
            } else {
                sb.append(c);
            }
            i = i + 1;
        }
        sb.append('"');
        return sb.toString();
    }

    static String jsonString(String s) {
        return javaString(s);
    }

    static boolean isIdent(String s) {
        if (s == null || s.length() == 0) {
            return false;
        }
        char c0 = s.charAt(0);
        if (!(c0 == '_' || (c0 >= 'a' && c0 <= 'z') || (c0 >= 'A' && c0 <= 'Z'))) {
            return false;
        }
        int i = 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (!(c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'))) {
                return false;
            }
            i = i + 1;
        }
        return !isKeyword(s);
    }

    static boolean isKeyword(String s) {
        String[] kw = new String[] { "abstract", "boolean", "break", "byte", "case", "catch", "char", "class", "continue",
                "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "if", "implements",
                "import", "instanceof", "int", "interface", "long", "native", "new", "null", "package", "private",
                "protected", "public", "return", "short", "static", "super", "switch", "this", "throw", "throws", "try",
                "void", "volatile", "while", "true", "false" };
        int i = 0;
        while (i < kw.length) {
            if (kw[i].equals(s)) {
                return true;
            }
            i = i + 1;
        }
        return false;
    }

    static String capitalize(String s) {
        if (s.length() == 0) {
            return s;
        }
        char c = s.charAt(0);
        if (c >= 'a' && c <= 'z') {
            return (char) (c - 32) + s.substring(1);
        }
        return s;
    }

    static String lowerFirst(String s) {
        if (s.length() == 0) {
            return s;
        }
        char c = s.charAt(0);
        if (c >= 'A' && c <= 'Z') {
            return (char) (c + 32) + s.substring(1);
        }
        return s;
    }

    static int hexVal(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }

    /** "#RRGGBB" or "#RRGGBBAA" -> packed 0xRRGGBBAA, or -1 on error (bit pattern, not a valid colour). */
    static long parseColor(String s) {
        if (s == null || s.length() < 7 || s.charAt(0) != '#') {
            return -1;
        }
        long v = 0;
        int i = 1;
        while (i < s.length()) {
            int h = hexVal(s.charAt(i));
            if (h < 0) {
                return -1;
            }
            v = v * 16 + h;
            i = i + 1;
        }
        if (s.length() == 7) {
            v = v * 256 + 255;
        } else if (s.length() != 9) {
            return -1;
        }
        return v;
    }

    static String hex2(int v) {
        String digits = "0123456789abcdef";
        return "" + digits.charAt((v / 16) % 16) + digits.charAt(v % 16);
    }

    static String colorHex(int r, int g, int b, int a) {
        String s = "#" + hex2(r) + hex2(g) + hex2(b);
        if (a != 255) {
            s = s + hex2(a);
        }
        return s;
    }
}
