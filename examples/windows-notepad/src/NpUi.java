/**
 * Small UI/text helpers shared by the Notepad windows (no nested classes in JNative).
 *
 * @author 张晓达
 */
class NpUi {
    static int rgb(int r, int g, int b) {
        return Component.rgba(r, g, b, 255);
    }

    static boolean isWindows() {
        return UiPlatform.family() == 2;
    }

    /** Keep the "&amp;" mnemonic marker only where the native menu understands it (Win32). */
    static String mn(String s) {
        if (isWindows()) {
            return s;
        }
        return s.replace("&", "");
    }

    static void place(Component c, int x, int y, int w, int h) {
        if (w < 1) {
            w = 1;
        }
        if (h < 1) {
            h = 1;
        }
        if (c.x == x && c.y == y && c.width == w && c.height == h) {
            return;
        }
        c.setBounds(x, y, w, h);
    }

    static Label label(Container parent, String text, int x, int y, int w, int h) {
        Label l = new Label(text);
        parent.add(l);
        l.setBounds(x, y, w, h);
        return l;
    }

    static Button button(Container parent, String text, int x, int y, int w, int h) {
        Button b = new Button(text);
        parent.add(b);
        b.setBounds(x, y, w, h);
        return b;
    }

    static Panel line(Container parent, int x, int y, int w, int h, int color) {
        Panel p = new Panel();
        p.setBackgroundRgba(color);
        parent.add(p);
        p.setBounds(x, y, w, h);
        return p;
    }

    /** Etched 1px frame (group box look). */
    static void frame(Container parent, int x, int y, int w, int h, int color) {
        line(parent, x, y, w, 1, color);
        line(parent, x, y + h - 1, w, 1, color);
        line(parent, x, y, 1, h, color);
        line(parent, x + w - 1, y, 1, h, color);
    }

    static String baseName(String path) {
        int a = path.lastIndexOf("\\");
        int b = path.lastIndexOf("/");
        int i = a;
        if (b > i) {
            i = b;
        }
        if (i < 0) {
            return path;
        }
        return path.substringFrom(i + 1);
    }

    /** Non-negative decimal integer, or -1 when s is not a valid number. */
    static int parseInt(String s) {
        if (s == null) {
            return -1;
        }
        String t = s.trim();
        int n = t.length();
        if (n == 0 || n > 9) {
            return -1;
        }
        int v = 0;
        int i = 0;
        while (i < n) {
            int c = t.charAt(i);
            if (c < 48 || c > 57) {
                return -1;
            }
            v = v * 10 + (c - 48);
            i = i + 1;
        }
        return v;
    }

    /** application/x-www-form-urlencoded (UTF-8). */
    static String urlEncode(String s) {
        byte[] d = s.getBytes();
        String hex = "0123456789ABCDEF";
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < d.length) {
            int c = d[i] & 255;
            boolean plain = (c >= 48 && c <= 57) || (c >= 65 && c <= 90) || (c >= 97 && c <= 122)
                    || c == 45 || c == 46 || c == 95 || c == 126;
            if (plain) {
                sb.append("" + singleChar(c));
            } else if (c == 32) {
                sb.append("+");
            } else {
                sb.append("%");
                sb.append(hex.substring(c / 16, c / 16 + 1));
                sb.append(hex.substring(c % 16, c % 16 + 1));
            }
            i = i + 1;
        }
        return sb.toString();
    }

    /** One ASCII character as a String (no char literals / valueOf(char) in JNative). */
    static String singleChar(int c) {
        byte[] one = new byte[1];
        one[0] = (byte) c;
        return String.fromBytes(one);
    }
}
