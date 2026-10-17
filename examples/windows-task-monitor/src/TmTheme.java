/**
 * JNative Windows Task Monitor - theme tokens, fonts, number formatting, sort helper.
 * All lengths are DIP (device independent pixels at 96 dpi); the canvas backend scales them.
 *
 * @author 张晓达
 */
class Th {
    /* 0 light, 1 dark */
    static int dark = 0;
    /* theme mode setting: 0 follow system, 1 light, 2 dark */
    static int mode = 0;

    static int bg;
    static int panel;
    static int panelAlt;
    static int navSel;
    static int navHover;
    static int text;
    static int sub;
    static int faint;
    static int line;
    static int lineSoft;
    static int accent;
    static int accentText;
    static int hover;
    static int rowSel;
    static int rowSelHover;
    static int heat1;
    static int heat2;
    static int heat3;
    static int heat4;
    static int heat5;
    static int thumb;
    static int thumbHover;
    static int editBg;
    static int editLine;
    static int btnBg;
    static int btnHover;
    static int danger;
    static int ok;
    static int graphLine;
    static int graphFill;
    static int graphGrid;
    static int graphBg;
    static int tipBg;
    static int disabled;
    static int selA;
    static int hoverA;

    static int fBody;
    static int fBold;
    static int fSmall;
    static int fBig;
    static int fTitle;
    static int fSub;
    static int fMed;
    static int fHuge;
    static int fIcon;
    static int fIconS;
    static int fIconL;
    static int fMono;
    static int fStat;
    static int ready = 0;

    /* layout metrics */
    static int STRIP_H = 44;
    static int NAV_W = 216;
    static int NAV_W_COLLAPSED = 56;
    static int NAV_ITEM_H = 40;
    static int CMD_H = 49;
    static int HDR_H = 49;
    static int ROW_H = 28;
    static int SECTION_H = 36;
    static int RADIUS = 8;

    static int rgb(int r, int g, int b) {
        return Component.rgba(r, g, b, 255);
    }

    static int argb(int r, int g, int b, int a) {
        return Component.rgba(r, g, b, a);
    }

    static void initFonts() {
        if (ready != 0) {
            return;
        }
        ready = 1;
        fBody = Gfx.font("Segoe UI Variable Text", 120, 400);
        fBold = Gfx.font("Segoe UI Variable Text", 120, 600);
        fSmall = Gfx.font("Segoe UI Variable Text", 110, 400);
        fSub = Gfx.font("Segoe UI Variable Text", 130, 400);
        fMed = Gfx.font("Segoe UI Variable Display", 150, 600);
        fBig = Gfx.font("Segoe UI Variable Display", 170, 400);
        fTitle = Gfx.font("Segoe UI Variable Display", 200, 600);
        fHuge = Gfx.font("Segoe UI Variable Display", 280, 400);
        fIcon = Gfx.font("Segoe Fluent Icons", 160, 400);
        fIconS = Gfx.font("Segoe Fluent Icons", 120, 400);
        fIconL = Gfx.font("Segoe Fluent Icons", 200, 400);
        fMono = Gfx.font("Consolas", 120, 400);
        fStat = Gfx.font("Segoe UI Variable Display", 210, 400);
    }

    /** Applies the palette for the effective (resolved) theme. */
    static void apply(int isDark) {
        dark = isDark;
        if (isDark == 0) {
            bg = rgb(238, 244, 249);
            panel = rgb(255, 255, 255);
            panelAlt = rgb(249, 249, 249);
            navSel = rgb(230, 235, 240);
            navHover = rgb(228, 234, 240);
            text = rgb(27, 27, 27);
            sub = rgb(96, 96, 96);
            faint = rgb(140, 140, 140);
            line = rgb(225, 225, 225);
            lineSoft = rgb(240, 240, 240);
            accent = rgb(0, 103, 192);
            accentText = rgb(255, 255, 255);
            hover = rgb(243, 243, 243);
            rowSel = rgb(204, 228, 247);
            rowSelHover = rgb(190, 220, 244);
            heat1 = rgb(214, 241, 255);
            heat2 = rgb(156, 221, 255);
            heat3 = rgb(84, 201, 255);
            heat4 = rgb(255, 214, 153);
            heat5 = rgb(255, 160, 140);
            thumb = rgb(133, 133, 133);
            thumbHover = rgb(100, 100, 100);
            editBg = rgb(251, 251, 251);
            editLine = rgb(225, 225, 225);
            btnBg = rgb(251, 251, 251);
            btnHover = rgb(245, 245, 245);
            danger = rgb(196, 43, 28);
            ok = rgb(15, 123, 15);
            graphLine = rgb(17, 125, 187);
            graphFill = argb(17, 125, 187, 40);
            graphGrid = rgb(217, 234, 244);
            graphBg = rgb(255, 255, 255);
            tipBg = rgb(255, 255, 255);
            disabled = rgb(160, 160, 160);
            selA = argb(0, 103, 192, 40);
            hoverA = argb(0, 0, 0, 14);
        } else {
            bg = rgb(32, 32, 32);
            panel = rgb(43, 43, 43);
            panelAlt = rgb(50, 50, 50);
            navSel = rgb(51, 51, 51);
            navHover = rgb(45, 45, 45);
            text = rgb(255, 255, 255);
            sub = rgb(200, 200, 200);
            faint = rgb(150, 150, 150);
            line = rgb(64, 64, 64);
            lineSoft = rgb(55, 55, 55);
            accent = rgb(96, 205, 255);
            accentText = rgb(0, 0, 0);
            hover = rgb(55, 55, 55);
            rowSel = rgb(57, 80, 100);
            rowSelHover = rgb(63, 90, 112);
            heat1 = rgb(35, 62, 84);
            heat2 = rgb(30, 84, 118);
            heat3 = rgb(19, 108, 154);
            heat4 = rgb(130, 98, 28);
            heat5 = rgb(150, 70, 60);
            thumb = rgb(150, 150, 150);
            thumbHover = rgb(190, 190, 190);
            editBg = rgb(45, 45, 45);
            editLine = rgb(70, 70, 70);
            btnBg = rgb(55, 55, 55);
            btnHover = rgb(65, 65, 65);
            danger = rgb(255, 153, 164);
            ok = rgb(108, 203, 95);
            graphLine = rgb(96, 205, 255);
            graphFill = argb(96, 205, 255, 40);
            graphGrid = rgb(60, 70, 78);
            graphBg = rgb(36, 36, 36);
            tipBg = rgb(50, 50, 50);
            disabled = rgb(110, 110, 110);
            selA = argb(96, 190, 255, 55);
            hoverA = argb(255, 255, 255, 14);
        }
    }

    static int heatColor(int tier) {
        if (tier <= 0) {
            return 0;
        }
        if (tier == 1) {
            return heat1;
        }
        if (tier == 2) {
            return heat2;
        }
        if (tier == 3) {
            return heat3;
        }
        if (tier == 4) {
            return heat4;
        }
        return heat5;
    }
}

class Fmt {
    static String two(int n) {
        if (n < 10) {
            return "0" + String.valueOf(n);
        }
        return String.valueOf(n);
    }

    /** Thousands separators for a non-negative long. */
    static String comma(long n) {
        if (n < 0) {
            return "-" + comma(-n);
        }
        String s = AmParse.longToString(n);
        int len = s.length();
        if (len <= 3) {
            return s;
        }
        String out = "";
        int i = 0;
        while (i < len) {
            if (i > 0 && (len - i) % 3 == 0) {
                out = out + ",";
            }
            out = out + s.substring(i, i + 1);
            i = i + 1;
        }
        return out;
    }

    /** x10 = value * 10 -> "12.3". */
    static String dec1(long x10) {
        if (x10 < 0) {
            return "-" + dec1(-x10);
        }
        long w = x10 / 10;
        long f = x10 % 10;
        return comma(w) + "." + AmParse.longToString(f);
    }

    static String dec2(long x100) {
        if (x100 < 0) {
            return "-" + dec2(-x100);
        }
        long w = x100 / 100;
        long f = x100 % 100;
        String fs = AmParse.longToString(f);
        if (f < 10) {
            fs = "0" + fs;
        }
        return AmParse.longToString(w) + "." + fs;
    }

    /** Percent given tenths of percent: 0 -> "0%", 67 -> "6.7%". */
    static String pct(int x10) {
        if (x10 < 0) {
            return "—";
        }
        if (x10 == 0) {
            return "0%";
        }
        if (x10 >= 1000) {
            return "100%";
        }
        if (x10 % 10 == 0) {
            return String.valueOf(x10 / 10) + "%";
        }
        return String.valueOf(x10 / 10) + "." + String.valueOf(x10 % 10) + "%";
    }

    static String pctPlain(int x10) {
        return pct(x10);
    }

    /** Whole percent for the column header (rounded). */
    static String pctWhole(int x10) {
        if (x10 < 0) {
            return "—";
        }
        int w = (x10 + 5) / 10;
        if (w > 100) {
            w = 100;
        }
        return String.valueOf(w) + "%";
    }

    /** Process-list memory: always MB with one decimal. */
    static String memMB(long bytes) {
        if (bytes < 0) {
            return "—";
        }
        long x10 = (bytes * 10 + 524288) / 1048576;
        return dec1(x10) + " MB";
    }

    /** Auto unit (B/KB/MB/GB/TB) with sensible precision. */
    static String bytes(long b) {
        if (b < 0) {
            return "—";
        }
        if (b < 1024) {
            return AmParse.longToString(b) + " B";
        }
        if (b < 1048576) {
            return dec1((b * 10 + 512) / 1024) + " KB";
        }
        if (b < 1073741824L) {
            return dec1((b * 10 + 524288) / 1048576) + " MB";
        }
        if (b < 1099511627776L) {
            return dec1((b * 10 + 536870912L) / 1073741824L) + " GB";
        }
        return dec1((b / 109951162778L)) + " TB";
    }

    /** Capacity style "15.7 GB" (1 decimal GB). */
    static String gb(long b) {
        if (b < 0) {
            return "—";
        }
        return dec1((b * 10 + 536870912L) / 1073741824L) + " GB";
    }

    /** MB/s with one decimal like Task Manager: "0 MB/秒", "0.1 MB/秒". */
    static String diskRate(long bps) {
        if (bps < 0) {
            return "—";
        }
        if (bps == 0) {
            return "0 MB/秒";
        }
        long x10 = (bps * 10 + 524288) / 1048576;
        if (x10 == 0) {
            return "0.1 MB/秒";
        }
        return dec1(x10) + " MB/秒";
    }

    /** Detailed rate: B/s, KB/s, MB/s. */
    static String rate(long bps) {
        if (bps < 0) {
            return "—";
        }
        if (bps < 1024) {
            return AmParse.longToString(bps) + " B/秒";
        }
        if (bps < 1048576) {
            return dec1((bps * 10 + 512) / 1024) + " KB/秒";
        }
        return dec1((bps * 10 + 524288) / 1048576) + " MB/秒";
    }

    /** bits-per-second based rate, "1.2 Mbps". */
    static String bitRate(long bytesPerSec) {
        if (bytesPerSec < 0) {
            return "—";
        }
        long bits = bytesPerSec * 8;
        if (bits < 1000) {
            return AmParse.longToString(bits) + " bps";
        }
        if (bits < 1000000) {
            return dec1((bits * 10 + 500) / 1000) + " Kbps";
        }
        if (bits < 1000000000L) {
            return dec1((bits * 10 + 500000) / 1000000) + " Mbps";
        }
        return dec1((bits * 10 + 500000000L) / 1000000000L) + " Gbps";
    }

    static String linkSpeed(long bps) {
        if (bps <= 0) {
            return "—";
        }
        if (bps < 1000000) {
            return dec1((bps * 10 + 500) / 1000) + " Kbps";
        }
        if (bps < 1000000000L) {
            return AmParse.longToString((bps + 500000) / 1000000) + " Mbps";
        }
        return dec1((bps * 10 + 500000000L) / 1000000000L) + " Gbps";
    }

    /** Kbps with up to one decimal (bytes/s in). */
    static String kbps(long bytesPerSec) {
        if (bytesPerSec < 0) {
            return "—";
        }
        long x10 = (bytesPerSec * 8L * 10L + 500L) / 1000L;
        return dec1(x10);
    }

    static String ghz(int mhz) {
        if (mhz <= 0) {
            return "—";
        }
        return dec2((mhz + 5) / 10) + " GHz";
    }

    /** d:hh:mm:ss */
    static String uptime(long sec) {
        if (sec < 0) {
            return "—";
        }
        long d = sec / 86400;
        long h = (sec % 86400) / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        return AmParse.longToString(d) + ":" + two((int) h) + ":" + two((int) m) + ":" + two((int) s);
    }

    /** CPU time ns -> h:mm:ss */
    static String cpuTime(long ns) {
        if (ns < 0) {
            return "—";
        }
        long sec = ns / 1000000000L;
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        return AmParse.longToString(h) + ":" + two((int) m) + ":" + two((int) s);
    }

    static String nonNeg(long v) {
        if (v < 0) {
            return "—";
        }
        return comma(v);
    }

    static String stripExe(String name) {
        if (name == null) {
            return "";
        }
        if (name.length() > 4 && name.toLowerCase().endsWith(".exe")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }

    static String lower(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase();
    }
}

/** Stable merge sort of TmRow lists by numeric or string key. */
class TmSort {
    static int cmp(TmRow a, TmRow b, int useStr, int asc) {
        int c = 0;
        if (useStr != 0) {
            c = a.ss.compareTo(b.ss);
        } else if (a.sn < b.sn) {
            c = -1;
        } else if (a.sn > b.sn) {
            c = 1;
        }
        if (c == 0) {
            c = a.tie.compareTo(b.tie);
        }
        if (asc == 0) {
            c = -c;
        }
        return c;
    }

    static void sort(ArrayList<TmRow> a, int useStr, int asc) {
        int n = a.size();
        if (n < 2) {
            return;
        }
        ArrayList<TmRow> tmp = new ArrayList<TmRow>();
        int i = 0;
        while (i < n) {
            tmp.add(a.get(i));
            i = i + 1;
        }
        msort(a, tmp, 0, n, useStr, asc);
    }

    /* sorts a[lo,hi) using tmp as scratch */
    static void msort(ArrayList<TmRow> a, ArrayList<TmRow> tmp, int lo, int hi, int useStr, int asc) {
        if (hi - lo < 2) {
            return;
        }
        int mid = (lo + hi) / 2;
        msort(a, tmp, lo, mid, useStr, asc);
        msort(a, tmp, mid, hi, useStr, asc);
        int i = lo;
        int j = mid;
        int k = lo;
        while (i < mid && j < hi) {
            if (cmp(a.get(j), a.get(i), useStr, asc) < 0) {
                tmp.set(k, a.get(j));
                j = j + 1;
            } else {
                tmp.set(k, a.get(i));
                i = i + 1;
            }
            k = k + 1;
        }
        while (i < mid) {
            tmp.set(k, a.get(i));
            i = i + 1;
            k = k + 1;
        }
        while (j < hi) {
            tmp.set(k, a.get(j));
            j = j + 1;
            k = k + 1;
        }
        k = lo;
        while (k < hi) {
            a.set(k, tmp.get(k));
            k = k + 1;
        }
    }
}
