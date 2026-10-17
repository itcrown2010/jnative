/**
 * JNative Windows Task Monitor - hit registry, drawing primitives and the table engine.
 *
 * @author 张晓达
 */
class TmHit {
    static int[] hx = new int[512];
    static int[] hy = new int[512];
    static int[] hw = new int[512];
    static int[] hh = new int[512];
    static int[] hid = new int[512];
    static int[] hdata = new int[512];
    static int n = 0;

    static void reset() {
        n = 0;
    }

    static void add(int x, int y, int w, int h, int id, int data) {
        if (n >= 512 || w <= 0 || h <= 0) {
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

    /** Index of the topmost hit under (x,y) or -1. */
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

class TmRow {
    /* 0 item, 1 section header */
    int kind;
    String key;
    int depth;
    int expandable;
    int expanded;
    ArrayList<String> cells;
    int[] heat;
    String icon;
    int iconGlyph;
    int pid;
    String ref;
    int status;
    long sn;
    String ss;
    String tie;
    int dim;
    String members;
    int count;
    int cat;
    long cpuAcc;
    long ioAcc;
    long wsAcc;
    int diskAcc;
    int cpuX10;
    long wsSort;
    long diskSort;
    int anyWin;
    TmProc root;
    int ctxKind;
    int ownerSession;
    long ownerLogon;
    int selfApp;
    ArrayList<TmProc> mem;

    TmRow() {
        kind = 0;
        key = "";
        depth = 0;
        expandable = 0;
        expanded = 0;
        cells = new ArrayList<String>();
        heat = new int[16];
        icon = "";
        iconGlyph = 0;
        pid = 0;
        ref = "";
        status = 0;
        sn = 0;
        ss = "";
        tie = "";
        dim = 0;
        members = "";
        count = 1;
        cat = 0;
        root = null;
        ctxKind = 0;
        ownerSession = -1;
        mem = null;
    }

    void cell(String s) {
        cells.add(s);
    }
}

class TmCol {
    int id;
    String title;
    String big;
    int w;
    int minW;
    int align;
    int visible;
    int defaultDesc;
    int heatBase;
    int sortable;

    TmCol(int cid, String t, int width, int al, int vis, int descFirst) {
        id = cid;
        title = t;
        big = "";
        w = width;
        minW = 48;
        align = al;
        visible = vis;
        defaultDesc = descFirst;
        heatBase = 0;
        sortable = 1;
    }
}

/** Static drawing helpers shared by all pages. */
class TmUi {
    static int mx = -1;
    static int my = -1;

    static int hot(int x, int y, int w, int h) {
        if (mx >= x && mx < x + w && my >= y && my < y + h) {
            return 1;
        }
        return 0;
    }

    /** Icon-font glyph centered in a box. */
    static void glyph(int cp, int x, int y, int w, int h, int color, int font) {
        Gfx.glyph(cp, x, y, w, h, color, font, 1 + 4);
    }

    static void text(String s, int x, int y, int w, int h, int color, int font) {
        Gfx.text(s, x, y, w, h, color, font, 4 + 8);
    }

    static void textR(String s, int x, int y, int w, int h, int color, int font) {
        Gfx.text(s, x, y, w, h, color, font, 2 + 4 + 8);
    }

    static void textC(String s, int x, int y, int w, int h, int color, int font) {
        Gfx.text(s, x, y, w, h, color, font, 1 + 4 + 8);
    }

    /** Command-bar style button: [glyph] label. Returns its width. */
    static int cmdButton(int x, int y, int h, int glyphCp, String label, int enabled, int hitId, int data) {
        int tw = 0;
        if (label.length() > 0) {
            tw = Gfx.textWidth(Th.fBody, label);
        }
        int w = 12 + 8;
        if (glyphCp > 0) {
            w = w + 16;
        }
        if (label.length() > 0) {
            w = w + tw;
            if (glyphCp > 0) {
                w = w + 8;
            }
        }
        int col = Th.text;
        if (enabled == 0) {
            col = Th.disabled;
        }
        if (enabled != 0 && hot(x, y, w, h) != 0) {
            Gfx.round(x, y, w, h, 4, Th.hover);
        }
        int cx = x + 10;
        if (glyphCp > 0) {
            glyph(glyphCp, cx, y, 16, h, col, Th.fIcon);
            cx = cx + 24;
        }
        if (label.length() > 0) {
            text(label, cx, y, tw + 4, h, col, Th.fBody);
        }
        if (enabled != 0) {
            TmHit.add(x, y, w, h, hitId, data);
        }
        return w;
    }


    /** Efficiency-mode leaf (14x14). */
    static void leaf(int x, int y, int color) {
        Gfx.polyBegin();
        Gfx.polyPt((x + 1) * 1000, (y + 13) * 1000);
        Gfx.polyPt((x + 2) * 1000, (y + 6) * 1000);
        Gfx.polyPt((x + 6) * 1000, (y + 2) * 1000);
        Gfx.polyPt((x + 12) * 1000, (y + 1) * 1000);
        Gfx.polyPt((x + 13) * 1000, (y + 4) * 1000);
        Gfx.polyPt((x + 11) * 1000, (y + 9) * 1000);
        Gfx.polyPt((x + 7) * 1000, (y + 12) * 1000);
        Gfx.polyPt((x + 3) * 1000, (y + 12) * 1000);
        Gfx.polyEnd(0, color, 14);
        Gfx.line(x + 2, y + 13, x + 9, y + 6, color, 14);
    }
    static void thinLine(int x1, int y1, int x2, int y2, int color) {
        Gfx.fill(x1, y1, Math.max(1, x2 - x1), Math.max(1, y2 - y1), color);
    }

    /** Segmented usage graph (Task Manager style): grid + area + AA line. v in [0,maxV]. */
    static void graph(int x, int y, int w, int h, long[] data, int base, int count, long maxV, int lineColor, int fillColor) {
        Gfx.fill(x, y, w, h, Th.graphBg);
        int gi = 1;
        while (gi < 10) {
            int gx = x + (w * gi) / 10;
            Gfx.fill(gx, y, 1, h, Th.graphGrid);
            gi = gi + 1;
        }
        gi = 1;
        while (gi < 4) {
            int gy = y + (h * gi) / 4;
            Gfx.fill(x, gy, w, 1, Th.graphGrid);
            gi = gi + 1;
        }
        if (maxV <= 0) {
            maxV = 1;
        }
        Gfx.polyBegin();
        Gfx.polyPt(x * 1000, (y + h) * 1000);
        int i = 0;
        while (i < count) {
            long v = data[base + i];
            if (v < 0) {
                v = 0;
            }
            if (v > maxV) {
                v = maxV;
            }
            long px = (long) x * 1000L + ((long) w * 1000L * (long) i) / (long) (count - 1);
            long py = (long) (y + h) * 1000L - ((long) h * 1000L * v) / maxV;
            Gfx.polyPt((int) px, (int) py);
            i = i + 1;
        }
        Gfx.polyPt((x + w) * 1000, (y + h) * 1000);
        Gfx.polyEnd(fillColor, 0, 0);
        Gfx.polyBegin();
        i = 0;
        while (i < count) {
            long v = data[base + i];
            if (v < 0) {
                v = 0;
            }
            if (v > maxV) {
                v = maxV;
            }
            long px = (long) x * 1000L + ((long) w * 1000L * (long) i) / (long) (count - 1);
            long py = (long) (y + h) * 1000L - ((long) h * 1000L * v) / maxV;
            Gfx.polyPt((int) px, (int) py);
            i = i + 1;
        }
        Gfx.polyEnd(0, lineColor, 12);
        Gfx.roundStroke(x, y, w, h, 0, 0, lineColor, 10);
    }

    /** Switch toggle (settings). */
    static void toggle(int x, int y, int on) {
        if (on != 0) {
            Gfx.round(x, y, 40, 20, 10, Th.accent);
            Gfx.round(x + 22, y + 3, 14, 14, 7, Th.accentText);
        } else {
            Gfx.roundStroke(x, y, 40, 20, 10, 0, Th.sub, 10);
            Gfx.round(x + 5, y + 5, 10, 10, 5, Th.sub);
        }
    }

    static void radio(int x, int y, int on) {
        Gfx.roundStroke(x, y, 20, 20, 10, 0, Th.sub, 10);
        if (on != 0) {
            Gfx.round(x + 4, y + 4, 12, 12, 6, Th.accent);
        }
    }

    static void check(int x, int y, int on) {
        if (on != 0) {
            Gfx.round(x, y, 16, 16, 3, Th.accent);
            glyph(59198, x, y, 16, 16, Th.accentText, Th.fIconS);
        } else {
            Gfx.roundStroke(x, y, 16, 16, 3, 0, Th.sub, 10);
        }
    }
}

/** Table with dual-line column headers, section rows, expand/collapse, sorting and an overlay scrollbar. */
class TmGrid {
    int gid;
    ArrayList<TmCol> cols;
    ArrayList<TmRow> rows;
    String selKey;
    int sortCol;
    int sortAsc;
    int scroll;
    int contentH;
    int bx;
    int by;
    int bw;
    int bh;
    int hx;
    int hy;
    int hw;
    int hdrH;
    int hoverRow;
    int hoverHdr;
    int hoverSep;
    int dragSep;
    int dragStartX;
    int dragStartW;
    int dragThumb;
    int dragThumbY;
    int dragThumbScroll;
    int thumbHot;
    int[] colX;
    int[] colW;
    int nameW;
    int flexCol;
    int ctxWant;
    int rowsDirty;
    String emptyText;
    int lastClickRow;
    int stickyScroll;
    HashMap<String, Integer> expanded;
    int rowsCount;

    TmGrid(int id) {
        gid = id;
        cols = new ArrayList<TmCol>();
        rows = new ArrayList<TmRow>();
        selKey = "";
        sortCol = 0;
        sortAsc = 1;
        scroll = 0;
        contentH = 0;
        hdrH = 49;
        hoverRow = -1;
        hoverHdr = -1;
        hoverSep = -1;
        dragSep = -1;
        dragThumb = 0;
        thumbHot = 0;
        colX = new int[24];
        colW = new int[24];
        nameW = 200;
        flexCol = 0;
        ctxWant = 0;
        rowsDirty = 1;
        emptyText = "";
        lastClickRow = -1;
        expanded = new HashMap<String, Integer>();
        rowsCount = 0;
    }

    TmCol addCol(int cid, String t, int w, int align, int vis, int descFirst) {
        TmCol c = new TmCol(cid, t, w, align, vis, descFirst);
        cols.add(c);
        return c;
    }

    TmCol colById(int cid) {
        int i = 0;
        while (i < cols.size()) {
            if (cols.get(i).id == cid) {
                return cols.get(i);
            }
            i = i + 1;
        }
        return null;
    }

    int isExpanded(String key) {
        if (expanded.containsKey(key)) {
            return 1;
        }
        return 0;
    }

    void setExpanded(String key, int on) {
        if (on != 0) {
            expanded.put(key, Integer.valueOf(1));
        } else {
            expanded.remove(key);
        }
    }

    /** Visible column positions: sets colX/colW for visible columns, returns visible count. */
    int layoutCols(int totalW) {
        int fixed = 0;
        int n = 0;
        int i = 0;
        int firstVis = -1;
        while (i < cols.size()) {
            TmCol c = cols.get(i);
            if (c.visible != 0) {
                if (firstVis < 0) {
                    firstVis = i;
                } else {
                    fixed = fixed + c.w;
                }
                n = n + 1;
            }
            i = i + 1;
        }
        int first = Math.max(160, totalW - fixed);
        int x = 0;
        int k = 0;
        i = 0;
        while (i < cols.size()) {
            TmCol c = cols.get(i);
            if (c.visible != 0) {
                int w = c.w;
                if (i == firstVis) {
                    w = first;
                }
                colX[k] = x;
                colW[k] = w;
                x = x + w;
                k = k + 1;
            }
            i = i + 1;
        }
        return n;
    }

    TmCol visCol(int k) {
        int n = 0;
        int i = 0;
        while (i < cols.size()) {
            if (cols.get(i).visible != 0) {
                if (n == k) {
                    return cols.get(i);
                }
                n = n + 1;
            }
            i = i + 1;
        }
        return null;
    }

    int rowHeight(TmRow r) {
        if (r.kind == 1) {
            return Th.SECTION_H;
        }
        return Th.ROW_H;
    }

    int indexOfKey(String key) {
        int i = 0;
        while (i < rows.size()) {
            if (rows.get(i).key.equals(key)) {
                return i;
            }
            i = i + 1;
        }
        return -1;
    }

    TmRow selected() {
        if (selKey.length() == 0) {
            return null;
        }
        int i = indexOfKey(selKey);
        if (i < 0) {
            return null;
        }
        return rows.get(i);
    }

    int rowTop(int idx) {
        int y = 0;
        int i = 0;
        while (i < idx && i < rows.size()) {
            y = y + rowHeight(rows.get(i));
            i = i + 1;
        }
        return y;
    }

    void recomputeContent() {
        int h = 0;
        int i = 0;
        while (i < rows.size()) {
            h = h + rowHeight(rows.get(i));
            i = i + 1;
        }
        contentH = h;
        int maxS = Math.max(0, contentH - bh);
        if (scroll > maxS) {
            scroll = maxS;
        }
        if (scroll < 0) {
            scroll = 0;
        }
    }

    void ensureVisible(int idx) {
        if (idx < 0 || idx >= rows.size()) {
            return;
        }
        int top = rowTop(idx);
        int h = rowHeight(rows.get(idx));
        if (top < scroll) {
            scroll = top;
        } else if (top + h > scroll + bh) {
            scroll = top + h - bh;
        }
        recomputeContent();
    }

    /* ----------------------------------------------------------------- paint */
    void paint(int x, int y, int w, int h) {
        hx = x;
        hy = y;
        hw = w;
        bx = x;
        by = y + hdrH;
        bw = w;
        bh = Math.max(0, h - hdrH);
        int n = layoutCols(w);
        recomputeContent();
        paintHeader(x, y, w, n);
        paintBody(x, n);
    }

    void paintHeader(int x, int y, int w, int n) {
        Gfx.fill(x, y, w, hdrH, Th.panel);
        int k = 0;
        while (k < n) {
            TmCol c = visCol(k);
            int cx = x + colX[k];
            int cw = colW[k];
            int isSort = 0;
            if (c.id == sortCol) {
                isSort = 1;
            }
            if (hoverHdr == k && dragSep < 0) {
                Gfx.fill(cx, y, cw, hdrH, Th.hover);
            }
            int pad = 8;
            if (k == 0) {
                pad = 16;
            }
            if (c.align == 2) {
                if (c.big.length() > 0) {
                    TmUi.textR(c.big, cx + 4, y + 2, cw - 4 - pad, 28, Th.text, Th.fBig);
                }
                Gfx.text(c.title, cx + 4, y + hdrH - 23, cw - 4 - pad, 20, Th.sub, Th.fBody, 2 + 4 + 8);
            } else {
                Gfx.text(c.title, cx + pad, y + hdrH - 23, cw - pad - 4, 20, Th.sub, Th.fBody, 4 + 8);
            }
            if (isSort != 0 && c.sortable != 0) {
                int cp = 59150;
                if (sortAsc == 0) {
                    cp = 59149;
                }
                int gx = cx + cw / 2 - 6;
                if (c.align == 2) {
                    gx = cx + cw - pad - 22;
                }
                if (k == 0) {
                    gx = cx + cw / 2 - 6;
                }
                Gfx.glyph(cp, gx, y + 6, 12, 12, Th.sub, Th.fIconS, 1 + 4);
            }
            if (k > 0) {
                Gfx.fill(cx, y + 4, 1, hdrH - 8, Th.lineSoft);
            }
            if (c.sortable != 0) {
                TmHit.add(cx, y, cw - 4, hdrH, 100, gid * 100 + k);
            }
            k = k + 1;
        }
        Gfx.fill(x, y + hdrH - 1, w, 1, Th.line);
        /* resize handles */
        k = 0;
        while (k < n) {
            if (k > 0) {
                int sx = x + colX[k];
                if (hoverSep == k || dragSep == k) {
                    Gfx.fill(sx - 1, y + 2, 2, hdrH - 4, Th.sub);
                }
            }
            k = k + 1;
        }
    }

    void paintBody(int x, int n) {
        Gfx.clip(bx, by, bw, bh);
        Gfx.fill(bx, by, bw, bh, Th.panel);
        int yy = by - scroll;
        int i = 0;
        int maxIdx = rows.size();
        while (i < maxIdx) {
            TmRow r = rows.get(i);
            int rh = rowHeight(r);
            if (yy + rh >= by && yy <= by + bh) {
                try {
                    paintRow(r, i, yy, rh, n);
                } catch (Exception e) {
                    TmApp.paintErrors = TmApp.paintErrors + 1;
                }
            }
            yy = yy + rh;
            if (yy > by + bh) {
                break;
            }
            i = i + 1;
        }
        if (rows.size() == 0 && emptyText.length() > 0) {
            Gfx.text(emptyText, bx + 16, by + 24, bw - 32, 24, Th.sub, Th.fSub, 4 + 8);
        }
        Gfx.unclip();
        paintScrollbar();
    }

    void paintRow(TmRow r, int idx, int y, int rh, int n) {
        if (r.kind == 1) {
            Gfx.text(r.cells.get(0), bx + 16, y + 6, bw - 32, rh - 6, Th.text, Th.fMed, 4 + 8);
            return;
        }
        /* heat cells */
        int k = 0;
        while (k < n) {
            if (k < 16 && r.heat[k] > 0) {
                Gfx.fill(bx + colX[k], y, colW[k], rh, Th.heatColor(r.heat[k]));
            }
            k = k + 1;
        }
        int sel = 0;
        if (r.key.equals(selKey)) {
            sel = 1;
        }
        if (sel != 0) {
            Gfx.fill(bx, y, bw, rh, Th.selA);
        } else if (hoverRow == idx) {
            Gfx.fill(bx, y, bw, rh, Th.hoverA);
        }
        k = 0;
        while (k < n) {
            TmCol c = visCol(k);
            int cx = bx + colX[k];
            int cw = colW[k];
            String s = "";
            if (k < r.cells.size()) {
                s = r.cells.get(k);
            }
            int col = Th.text;
            if (r.dim != 0) {
                col = Th.sub;
            }
            if (k == 0) {
                int ind = r.depth * 24;
                int tx = cx + 16 + ind;
                if (r.expandable != 0 || r.depth == 0) {
                    /* chevron slot */
                    if (r.expandable != 0) {
                        int cp = 59244;
                        if (r.expanded != 0) {
                            cp = 59149;
                        }
                        Gfx.glyph(cp, cx + 12 + ind, y, 18, rh, Th.sub, Th.fIconS, 1 + 4);
                    }
                    tx = cx + 36 + ind;
                } else {
                    tx = cx + 36 + ind;
                }
                if (r.icon.length() > 0) {
                    Gfx.icon(r.icon, tx, y + (rh - 16) / 2, 16);
                    tx = tx + 24;
                } else if (r.iconGlyph > 0) {
                    Gfx.glyph(r.iconGlyph, tx, y, 16, rh, Th.sub, Th.fIcon, 1 + 4);
                    tx = tx + 24;
                }
                Gfx.text(s, tx, y, cx + cw - tx - 6, rh, col, Th.fBody, 4 + 8);
            } else if (c.align == 2) {
                Gfx.text(s, cx + 4, y, cw - 12, rh, col, Th.fBody, 2 + 4 + 8);
            } else if (c.align == 1) {
                Gfx.text(s, cx + 4, y, cw - 8, rh, col, Th.fBody, 1 + 4 + 8);
            } else {
                int tx2 = cx + 8;
                if (c.id == 1 && r.status > 0) {
                    /* status column: leaf icon for efficiency mode / pause */
                    int lx = cx + cw - 30;
                    if (s.length() > 0) {
                        lx = cx + 8;
                        tx2 = cx + 30;
                    }
                    if (r.status == 1) {
                        TmUi.leaf(lx, y + (rh - 14) / 2, Th.ok);
                    } else if (r.status == 2) {
                        Gfx.glyph(59241, lx, y, 16, rh, Th.sub, Th.fIconS, 1 + 4);
                    }
                }
                Gfx.text(s, tx2, y, cx + cw - tx2 - 6, rh, col, Th.fBody, 4 + 8);
            }
            k = k + 1;
        }
    }

    int thumbH() {
        if (contentH <= bh || bh <= 0) {
            return 0;
        }
        int th = (bh * bh) / contentH;
        if (th < 36) {
            th = 36;
        }
        return th;
    }

    int thumbY() {
        int th = thumbH();
        int track = bh - th;
        int maxS = Math.max(1, contentH - bh);
        return by + (track * scroll) / maxS;
    }

    void paintScrollbar() {
        int th = thumbH();
        if (th == 0) {
            return;
        }
        int ty = thumbY();
        int tw = 4;
        int col = Th.thumb;
        if (thumbHot != 0 || dragThumb != 0) {
            tw = 7;
            col = Th.thumbHover;
        }
        Gfx.round(bx + bw - tw - 3, ty, tw, th, tw / 2, col);
    }

    /* ---------------------------------------------------------------- events */
    int rowAt(int mxp, int myp) {
        if (myp < by || myp >= by + bh || mxp < bx || mxp >= bx + bw) {
            return -1;
        }
        int yy = by - scroll;
        int i = 0;
        while (i < rows.size()) {
            int rh = rowHeight(rows.get(i));
            if (myp >= yy && myp < yy + rh) {
                return i;
            }
            yy = yy + rh;
            i = i + 1;
        }
        return -1;
    }

    int sepAt(int mxp, int myp) {
        if (myp < hy || myp >= hy + hdrH) {
            return -1;
        }
        int n = layoutCols(hw);
        int k = 1;
        while (k < n) {
            int sx = hx + colX[k];
            if (mxp >= sx - 4 && mxp <= sx + 4) {
                return k;
            }
            k = k + 1;
        }
        return -1;
    }

    int hdrAt(int mxp, int myp) {
        if (myp < hy || myp >= hy + hdrH || mxp < hx || mxp >= hx + hw) {
            return -1;
        }
        int n = layoutCols(hw);
        int k = 0;
        while (k < n) {
            if (mxp >= hx + colX[k] && mxp < hx + colX[k] + colW[k]) {
                return k;
            }
            k = k + 1;
        }
        return -1;
    }

    void onMove(int mxp, int myp, int btn) {
        if (dragThumb != 0) {
            int th = thumbH();
            int track = Math.max(1, bh - th);
            int dy = myp - dragThumbY;
            scroll = dragThumbScroll + (dy * (contentH - bh)) / track;
            recomputeContent();
            return;
        }
        if (dragSep >= 0) {
            int k = dragSep;
            TmCol c = visCol(k);
            if (c != null) {
                int nw = dragStartW + (dragStartX - mxp);
                if (nw < c.minW) {
                    nw = c.minW;
                }
                if (nw > 600) {
                    nw = 600;
                }
                /* dragging the border to the LEFT of column k shrinks/grows column k (anchored right) */
                c.w = nw;
            }
            Gfx.cursor(1);
            return;
        }
        hoverRow = rowAt(mxp, myp);
        hoverHdr = hdrAt(mxp, myp);
        hoverSep = sepAt(mxp, myp);
        thumbHot = 0;
        if (thumbH() > 0 && mxp >= bx + bw - 14 && mxp < bx + bw && myp >= by && myp < by + bh) {
            thumbHot = 1;
        }
        if (hoverSep >= 0) {
            Gfx.cursor(1);
        }
    }

    void onLeave() {
        hoverRow = -1;
        hoverHdr = -1;
        hoverSep = -1;
        thumbHot = 0;
    }

    /** Returns: 0 nothing, 1 select/handled, 2 context menu wanted, 3 sort changed, 4 expand toggled. */
    int onDown(int mxp, int myp, int btn) {
        int sp = sepAt(mxp, myp);
        if (sp >= 0 && btn == 1) {
            dragSep = sp;
            dragStartX = mxp;
            dragStartW = colW[sp];
            return 1;
        }
        int hk = hdrAt(mxp, myp);
        if (hk >= 0) {
            if (btn == 2) {
                return 5;
            }
            TmCol c = visCol(hk);
            if (c != null && c.sortable != 0) {
                if (sortCol == c.id) {
                    if (sortAsc != 0) {
                        sortAsc = 0;
                    } else {
                        sortAsc = 1;
                    }
                } else {
                    sortCol = c.id;
                    if (c.defaultDesc != 0) {
                        sortAsc = 0;
                    } else {
                        sortAsc = 1;
                    }
                }
                return 3;
            }
            return 1;
        }
        if (thumbH() > 0 && mxp >= bx + bw - 14 && mxp < bx + bw && myp >= by && myp < by + bh) {
            int ty = thumbY();
            int th = thumbH();
            if (myp >= ty && myp < ty + th) {
                dragThumb = 1;
                dragThumbY = myp;
                dragThumbScroll = scroll;
            } else {
                if (myp < ty) {
                    scroll = scroll - bh;
                } else {
                    scroll = scroll + bh;
                }
                recomputeContent();
            }
            return 1;
        }
        int ri = rowAt(mxp, myp);
        if (ri < 0) {
            return 0;
        }
        TmRow r = rows.get(ri);
        if (r.kind == 1) {
            return 1;
        }
        selKey = r.key;
        lastClickRow = ri;
        if (btn == 1 && r.expandable != 0) {
            int ind = r.depth * 24;
            int cx = bx + 12 + ind;
            if (mxp >= cx - 4 && mxp < cx + 24) {
                setExpanded(r.key, 1 - r.expanded);
                return 4;
            }
        }
        if (btn == 2) {
            return 2;
        }
        return 1;
    }

    void onUp() {
        dragThumb = 0;
        dragSep = -1;
    }

    void onWheel(int delta) {
        int step = (delta * Th.ROW_H * 3) / 120;
        scroll = scroll - step;
        recomputeContent();
    }

    /** Keyboard. Returns 1 when handled. */
    int onKey(int vk, int mods) {
        if (rows.size() == 0) {
            return 0;
        }
        int cur = indexOfKey(selKey);
        if (vk == 40 || vk == 38) {
            int step = 1;
            if (vk == 38) {
                step = -1;
            }
            int i = cur;
            if (i < 0) {
                i = -1;
                if (step < 0) {
                    i = rows.size();
                }
            }
            i = i + step;
            while (i >= 0 && i < rows.size() && rows.get(i).kind == 1) {
                i = i + step;
            }
            if (i >= 0 && i < rows.size()) {
                selKey = rows.get(i).key;
                ensureVisible(i);
            }
            return 1;
        }
        if (vk == 36 || vk == 35) {
            int i = 0;
            int step = 1;
            if (vk == 35) {
                i = rows.size() - 1;
                step = -1;
            }
            while (i >= 0 && i < rows.size() && rows.get(i).kind == 1) {
                i = i + step;
            }
            if (i >= 0 && i < rows.size()) {
                selKey = rows.get(i).key;
                ensureVisible(i);
            }
            return 1;
        }
        if (vk == 33 || vk == 34) {
            int dir = 1;
            if (vk == 33) {
                dir = -1;
            }
            scroll = scroll + dir * Math.max(Th.ROW_H, bh - Th.ROW_H);
            recomputeContent();
            int i = cur;
            if (i < 0) {
                i = 0;
            }
            i = i + dir * Math.max(1, bh / Th.ROW_H - 1);
            if (i < 0) {
                i = 0;
            }
            if (i >= rows.size()) {
                i = rows.size() - 1;
            }
            while (i > 0 && i < rows.size() - 1 && rows.get(i).kind == 1) {
                i = i + dir;
            }
            selKey = rows.get(i).key;
            return 1;
        }
        if (cur >= 0) {
            TmRow r = rows.get(cur);
            if (vk == 39 && r.expandable != 0 && r.expanded == 0) {
                setExpanded(r.key, 1);
                return 4;
            }
            if (vk == 37 && r.expandable != 0 && r.expanded != 0) {
                setExpanded(r.key, 0);
                return 4;
            }
        }
        return 0;
    }
}
