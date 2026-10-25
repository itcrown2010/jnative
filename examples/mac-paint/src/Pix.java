/** 像素颜色工具：颜色按 JNative 图形库的 RGBA 打包（r | g<<8 | b<<16 | a<<24）。 */
public class Pix {
    static int rgb(int r, int g, int b) {
        return (r & 255) | ((g & 255) << 8) | ((b & 255) << 16) | (255 << 24);
    }

    static int r(int c) {
        return c & 255;
    }

    static int g(int c) {
        return (c >> 8) & 255;
    }

    static int b(int c) {
        return (c >> 16) & 255;
    }

    static int a(int c) {
        return (c >> 24) & 255;
    }

    static int WHITE = rgb(255, 255, 255);
    static int BLACK = rgb(0, 0, 0);

    static String hex2(int v) {
        String d = "0123456789ABCDEF";
        return d.substring((v >> 4) & 15, ((v >> 4) & 15) + 1) + d.substring(v & 15, (v & 15) + 1);
    }

    /** "#RRGGBB" */
    static String hex(int c) {
        return "#" + hex2(r(c)) + hex2(g(c)) + hex2(b(c));
    }

    static int hexDigit(int ch) {
        if (ch >= 48 && ch <= 57) {
            return ch - 48;
        }
        if (ch >= 97 && ch <= 102) {
            return ch - 97 + 10;
        }
        if (ch >= 65 && ch <= 70) {
            return ch - 65 + 10;
        }
        return -1;
    }

    /** 解析 "#RRGGBB" / "RRGGBB"；失败返回 fallback。 */
    static int parseHex(String s, int fallback) {
        String t = s.trim();
        if (t.startsWith("#")) {
            t = t.substring(1);
        }
        if (t.length() != 6) {
            return fallback;
        }
        int v = 0;
        int i = 0;
        while (i < 6) {
            int d = hexDigit(t.charAt(i));
            if (d < 0) {
                return fallback;
            }
            v = v * 16 + d;
            i = i + 1;
        }
        return rgb((v >> 16) & 255, (v >> 8) & 255, v & 255);
    }

    static int parseInt(String s, int fallback) {
        String t = s.trim();
        if (t.length() == 0 || t.length() > 9) {
            return fallback;
        }
        int v = 0;
        int i = 0;
        while (i < t.length()) {
            int c = t.charAt(i);
            if (c < 48 || c > 57) {
                return fallback;
            }
            v = v * 10 + (c - 48);
            i = i + 1;
        }
        return v;
    }

    static int clamp(int v, int lo, int hi) {
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }
}
