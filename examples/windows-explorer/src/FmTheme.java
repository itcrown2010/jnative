/**
 * JNative Windows Explorer - Windows 11 theme tokens, fonts, metrics and the canvas hit registry.
 * All lengths are DIP (device independent pixels at 96 dpi); the canvas backend scales them.
 *
 * @author 张晓达
 */
class Ft {
    static int dark = 0;
    static int bg;
    static int panel;
    static int panelLine;
    static int text;
    static int sub;
    static int disabled;
    static int accent;
    static int accentText;
    static int hover;
    static int pressed;
    static int rowSel;
    static int rowSelHover;
    static int rowHover;
    static int line;
    static int editBg;
    static int editLine;
    static int editLineBottom;
    static int thumb;
    static int thumbHover;
    static int band;
    static int bandLine;
    static int barBg;
    static int barFill;
    static int barWarn;
    static int folderGlyph;

    static int fBody;
    static int fBold;
    static int fSmall;
    static int fIcon;
    static int fIconS;
    static int fIconXS;
    static int ready = 0;

    static int NAV_H = 48;
    static int CMD_H = 44;
    static int STATUS_H = 28;
    static int HDR_H = 32;
    static int ROW_H = 28;
    static int NAV_ITEM_H = 32;
    static int GRID_W = 104;
    static int GRID_H = 92;
    static int TILE_W = 296;
    static int TILE_H = 64;

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
        fIcon = Gfx.font("Segoe Fluent Icons", 160, 400);
        fIconS = Gfx.font("Segoe Fluent Icons", 120, 400);
        fIconXS = Gfx.font("Segoe Fluent Icons", 90, 400);
    }

    static void apply(int isDark) {
        dark = isDark;
        if (isDark == 0) {
            bg = rgb(243, 243, 243);
            panel = rgb(255, 255, 255);
            panelLine = rgb(229, 229, 229);
            text = rgb(27, 27, 27);
            sub = rgb(96, 96, 96);
            disabled = rgb(160, 160, 160);
            accent = rgb(0, 95, 184);
            accentText = rgb(255, 255, 255);
            hover = rgb(232, 232, 232);
            pressed = rgb(224, 224, 224);
            rowSel = rgb(204, 228, 247);
            rowSelHover = rgb(190, 219, 243);
            rowHover = rgb(243, 243, 243);
            line = rgb(229, 229, 229);
            editBg = rgb(251, 251, 251);
            editLine = rgb(229, 229, 229);
            editLineBottom = rgb(138, 138, 138);
            thumb = rgb(150, 150, 150);
            thumbHover = rgb(110, 110, 110);
            band = argb(0, 95, 184, 40);
            bandLine = rgb(0, 95, 184);
            barBg = rgb(230, 230, 230);
            barFill = rgb(38, 160, 218);
            barWarn = rgb(218, 38, 38);
            folderGlyph = rgb(250, 190, 40);
        } else {
            bg = rgb(32, 32, 32);
            panel = rgb(39, 39, 39);
            panelLine = rgb(58, 58, 58);
            text = rgb(255, 255, 255);
            sub = rgb(200, 200, 200);
            disabled = rgb(120, 120, 120);
            accent = rgb(96, 205, 255);
            accentText = rgb(0, 0, 0);
            hover = rgb(55, 55, 55);
            pressed = rgb(66, 66, 66);
            rowSel = rgb(54, 76, 96);
            rowSelHover = rgb(62, 86, 108);
            rowHover = rgb(50, 50, 50);
            line = rgb(60, 60, 60);
            editBg = rgb(45, 45, 45);
            editLine = rgb(66, 66, 66);
            editLineBottom = rgb(160, 160, 160);
            thumb = rgb(150, 150, 150);
            thumbHover = rgb(200, 200, 200);
            band = argb(96, 205, 255, 40);
            bandLine = rgb(96, 205, 255);
            barBg = rgb(70, 70, 70);
            barFill = rgb(38, 160, 218);
            barWarn = rgb(218, 38, 38);
            folderGlyph = rgb(250, 190, 40);
        }
        applyAccent(isDark);
    }

    /** Number of accent presets; index 0 keeps the built-in Windows 11 blue. */
    static int ACCENTS = 6;
    static int accentIdx = 0;

    static String accentName(int i) {
        if (i == 1) {
            return "紫色";
        }
        if (i == 2) {
            return "绿色";
        }
        if (i == 3) {
            return "橙色";
        }
        if (i == 4) {
            return "红色";
        }
        if (i == 5) {
            return "粉色";
        }
        return "蓝色 (默认)";
    }

    static int pack(int r, int g, int b) {
        return r * 65536 + g * 256 + b;
    }

    /** Accent colour of a preset packed as r*65536+g*256+b, for the light (d=0) or dark (d=1) palette. */
    static int accentPacked(int i, int d) {
        if (i == 1) {
            if (d == 0) {
                return pack(117, 60, 180);
            }
            return pack(190, 150, 255);
        }
        if (i == 2) {
            if (d == 0) {
                return pack(16, 124, 16);
            }
            return pack(108, 203, 95);
        }
        if (i == 3) {
            if (d == 0) {
                return pack(202, 80, 16);
            }
            return pack(255, 170, 90);
        }
        if (i == 4) {
            if (d == 0) {
                return pack(196, 43, 28);
            }
            return pack(255, 153, 164);
        }
        if (i == 5) {
            if (d == 0) {
                return pack(191, 59, 141);
            }
            return pack(255, 140, 200);
        }
        if (d == 0) {
            return pack(0, 95, 184);
        }
        return pack(96, 205, 255);
    }

    /** (a * pct + b * (100 - pct)) / 100 */
    static int mix(int a, int b, int pct) {
        return (a * pct + b * (100 - pct)) / 100;
    }

    /** Re-derives every accent based token; preset 0 leaves the stock colours untouched. */
    static void applyAccent(int d) {
        if (accentIdx <= 0 || accentIdx >= ACCENTS) {
            return;
        }
        int p = accentPacked(accentIdx, d);
        int r = p / 65536;
        int g = (p / 256) % 256;
        int b = p % 256;
        accent = rgb(r, g, b);
        bandLine = accent;
        band = argb(r, g, b, 40);
        barFill = accent;
        if (d == 0) {
            rowSel = rgb(mix(r, 255, 20), mix(g, 255, 20), mix(b, 255, 20));
            rowSelHover = rgb(mix(r, 255, 26), mix(g, 255, 26), mix(b, 255, 26));
        } else {
            rowSel = rgb(mix(r, 39, 25), mix(g, 39, 25), mix(b, 39, 25));
            rowSelHover = rgb(mix(r, 39, 31), mix(g, 39, 31), mix(b, 39, 31));
        }
    }
}

/** Hit registry rebuilt on every paint; later (topmost) entries win. */
class FmHit {
    static int[] hx = new int[256];
    static int[] hy = new int[256];
    static int[] hw = new int[256];
    static int[] hh = new int[256];
    static int[] hid = new int[256];
    static int[] hdata = new int[256];
    static int n = 0;

    static void reset() {
        n = 0;
    }

    static void add(int x, int y, int w, int h, int id, int data) {
        if (n >= 256 || w <= 0 || h <= 0) {
            return;
        }
        hx[n] = x;
        hy[n] = y;
        hw[n] = w;
        hh[n] = h;
        hid[n] = id;
        hdata[n] = data;
        n = n + 1;
    }

    static int at(int x, int y) {
        int i = n - 1;
        while (i >= 0) {
            if (x >= hx[i] && x < hx[i] + hw[i] && y >= hy[i] && y < hy[i] + hh[i]) {
                return i;
            }
            i = i - 1;
        }
        return -1;
    }
}

/** Hit ids used by FmHit (plain ints; the compiler does not inline static finals). */
class FmId {
    static int BACK = 1;
    static int FORWARD = 2;
    static int UP = 3;
    static int REFRESH = 4;
    static int ADDR_BG = 5;
    static int CRUMB = 6;
    static int CRUMB_ARROW = 7;
    static int CMD_NEW = 10;
    static int CMD_CUT = 11;
    static int CMD_COPY = 12;
    static int CMD_PASTE = 13;
    static int CMD_RENAME = 14;
    static int CMD_DELETE = 15;
    static int CMD_SORT = 16;
    static int CMD_VIEW = 17;
    static int CMD_MORE = 18;
    static int CMD_PROPS = 19;
    static int VIEW_DETAILS = 30;
    static int VIEW_GRID = 31;
    static int NAV_ITEM = 40;
    static int NAV_ARROW = 41;
    static int SPLITTER = 42;
    static int SEARCH_CLEAR = 43;
}
