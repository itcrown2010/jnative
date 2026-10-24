import jnative.ui.*;
import jnative.graphics.*;

/**
 * Shared look & feel for the custom-drawn Studio widgets: colours, fonts and small drawing helpers.
 *
 * @author 张晓达
 */
public class Ide {
    static int BG = 0xF1F2F4;
    static int PANEL = 0xFFFFFF;
    static int PANEL_ALT = 0xF7F8FA;
    static int BORDER = 0xD7DAE0;
    static int TEXT = 0x1F2328;
    static int SUBTLE = 0x6B7280;
    static int FAINT = 0x9AA1AD;
    static int ACCENT = 0x0A64D8;
    static int ACCENT_SOFT = 0xDCE9FB;
    static int SEL = 0xD3E3FB;
    static int SEL_IDLE = 0xE4E6EB;
    static int HOVER = 0xEBEEF3;
    static int ERROR = 0xD13438;
    static int WARN = 0xB7791F;
    static int OK = 0x2E9D4F;
    static int TOOLBAR = 0xF8F9FB;

    static Font fUi;
    static Font fUiBold;
    static Font fSmall;
    static Font fMono;
    static Font fBig;

    static Font ui() {
        if (fUi == null) {
            fUi = Font.system(12);
        }
        return fUi;
    }

    static Font bold() {
        if (fUiBold == null) {
            fUiBold = Font.systemBold(12);
        }
        return fUiBold;
    }

    static Font small() {
        if (fSmall == null) {
            fSmall = Font.system(11);
        }
        return fSmall;
    }

    static Font mono() {
        if (fMono == null) {
            fMono = Font.mono(12);
        }
        return fMono;
    }

    static Font big() {
        if (fBig == null) {
            fBig = Font.systemBold(14);
        }
        return fBig;
    }

    static void rgb(Graphics g, int c) {
        g.setColorRgba((c / 65536) % 256, (c / 256) % 256, c % 256, 255);
    }

    static void rgba(Graphics g, int c, int a) {
        g.setColorRgba((c / 65536) % 256, (c / 256) % 256, c % 256, a);
    }

    static void fill(Graphics g, int c, double x, double y, double w, double h) {
        rgb(g, c);
        g.fillRect(x, y, w, h);
    }

    static void rfill(Graphics g, int c, double x, double y, double w, double h, double r) {
        rgb(g, c);
        g.fillRoundRect(x, y, w, h, r);
    }

    static void rstroke(Graphics g, int c, double x, double y, double w, double h, double r) {
        rgb(g, c);
        g.setLineWidth(1.0);
        g.drawRoundRect(x, y, w, h, r);
    }

    static void line(Graphics g, int c, double x1, double y1, double x2, double y2) {
        rgb(g, c);
        g.setLineWidth(1.0);
        g.drawLine(x1, y1, x2, y2);
    }

    static void text(Graphics g, String s, double x, double y, Font f, int c) {
        g.setFont(f);
        rgb(g, c);
        g.drawText(s, x, y);
    }

    static int textWidth(Graphics g, String s, Font f) {
        Size sz = g.measureText(s, f);
        return sz.width;
    }

    /** Draws text vertically centred in a row of the given height. */
    static void textRow(Graphics g, String s, double x, double rowY, double rowH, Font f, int c) {
        g.setFont(f);
        int lh = g.getFontMetrics(f).getLineHeight();
        rgb(g, c);
        g.drawText(s, x, rowY + (rowH - lh) / 2.0);
    }

    static String ellipsize(Graphics g, String s, Font f, int maxW) {
        if (maxW <= 4) {
            return "";
        }
        if (textWidth(g, s, f) <= maxW) {
            return s;
        }
        int lo = 0;
        int hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (textWidth(g, s.substring(0, mid) + "…", f) <= maxW) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return s.substring(0, lo) + "…";
    }

    static long lastDownMs = 0;
    static int lastDownX = 0;
    static int lastDownY = 0;
    static Component lastDownSrc;

    /** Native events carry no click count: detect double-clicks by time/position (call once per press). */
    static boolean doubleClick(Component src, MouseEvent e) {
        long now = System.currentTimeMillis();
        boolean dbl = e.button == 1 && lastDownSrc == src && now - lastDownMs < 400
                && Math.abs(e.x - lastDownX) <= 4 && Math.abs(e.y - lastDownY) <= 4;
        if (dbl) {
            lastDownMs = 0;
        } else {
            lastDownMs = now;
        }
        lastDownSrc = src;
        lastDownX = e.x;
        lastDownY = e.y;
        return dbl;
    }

    /** Position of a component in window coordinates (sum of parent offsets). */
    static int windowX(Component c) {
        int sum = 0;
        Component cur = c;
        while (cur != null && !(cur instanceof Window)) {
            sum = sum + cur.x;
            cur = cur.parent;
        }
        return sum;
    }

    static int windowY(Component c) {
        int sum = 0;
        Component cur = c;
        while (cur != null && !(cur instanceof Window)) {
            sum = sum + cur.y;
            cur = cur.parent;
        }
        return sum;
    }

    /** Small file / folder / component icons drawn with vector primitives. */
    static void icon(Graphics g, String kind, double x, double y, double s) {
        g.setAntialias(true);
        if (kind.equals("folder") || kind.equals("folderOpen")) {
            rfill(g, 0xE8B04A, x, y + s * 0.18, s, s * 0.72, 2);
            rfill(g, 0xF2C46A, x, y + s * 0.30, s, s * 0.60, 2);
            return;
        }
        if (kind.equals("project")) {
            rfill(g, ACCENT, x, y + 1, s, s - 2, 3);
            text(g, "J", x + s * 0.28, y + s * 0.12, Font.systemBold((int) (s * 0.7)), 0xFFFFFF);
            return;
        }
        if (kind.equals("java")) {
            badge(g, x, y, s, 0x3A7BD5, "J");
            return;
        }
        if (kind.equals("jnui")) {
            badge(g, x, y, s, 0x8E4EC6, "U");
            return;
        }
        if (kind.equals("json")) {
            badge(g, x, y, s, 0x6B8E23, "{");
            return;
        }
        if (kind.equals("md")) {
            badge(g, x, y, s, 0x607D8B, "M");
            return;
        }
        if (kind.equals("error")) {
            rgb(g, ERROR);
            g.fillEllipse(x, y, s, s);
            text(g, "!", x + s * 0.38, y + s * 0.02, Font.systemBold((int) (s * 0.8)), 0xFFFFFF);
            return;
        }
        if (kind.equals("warning")) {
            rgb(g, WARN);
            g.fillEllipse(x, y, s, s);
            text(g, "!", x + s * 0.38, y + s * 0.02, Font.systemBold((int) (s * 0.8)), 0xFFFFFF);
            return;
        }
        if (kind.equals("form")) {
            rfill(g, 0xFFFFFF, x, y + 1, s, s - 2, 2);
            rstroke(g, 0x8E4EC6, x, y + 1, s, s - 2, 2);
            fill(g, 0x8E4EC6, x + 1, y + 2, s - 2, s * 0.22);
            return;
        }
        if (kind.equals("comp")) {
            rfill(g, 0x9AA7B8, x, y + 1, s, s - 2, 3);
            return;
        }
        badge(g, x, y, s, 0x9AA1AD, "");
    }

    static void badge(Graphics g, double x, double y, double s, int color, String letter) {
        rfill(g, color, x, y + 1, s, s - 2, 3);
        if (letter.length() > 0) {
            Font f = Font.systemBold((int) (s * 0.62));
            g.setFont(f);
            Size sz = g.measureText(letter, f);
            rgb(g, 0xFFFFFF);
            g.drawText(letter, x + (s - sz.width) / 2.0, y + 1 + (s - 2 - sz.height) / 2.0);
        }
    }

    /** Disclosure triangle: expanded points down, collapsed points right. */
    static void arrow(Graphics g, double cx, double cy, boolean expanded, int color) {
        g.setAntialias(true);
        rgb(g, color);
        g.setLineWidth(1.4);
        if (expanded) {
            g.drawLine(cx - 3.5, cy - 1.5, cx, cy + 2.0);
            g.drawLine(cx, cy + 2.0, cx + 3.5, cy - 1.5);
        } else {
            g.drawLine(cx - 1.5, cy - 3.5, cx + 2.0, cy);
            g.drawLine(cx + 2.0, cy, cx - 1.5, cy + 3.5);
        }
    }

    static String fileExt(String name) {
        int d = name.lastIndexOf(".");
        if (d < 0) {
            return "";
        }
        return name.substring(d + 1);
    }

    static String iconForFile(String name) {
        String e = fileExt(name);
        if (e.equals("java")) {
            return "java";
        }
        if (e.equals("jnui")) {
            return "jnui";
        }
        if (e.equals("project") || e.equals("json")) {
            return "json";
        }
        if (e.equals("md")) {
            return "md";
        }
        return "file";
    }

    static String baseName(String path) {
        int s = path.lastIndexOf("/");
        if (s < 0) {
            return path;
        }
        return path.substring(s + 1);
    }

    static String dirName(String path) {
        int s = path.lastIndexOf("/");
        if (s <= 0) {
            return "";
        }
        return path.substring(0, s);
    }
}
