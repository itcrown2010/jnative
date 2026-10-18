/**
 * JNative Windows Explorer - the file list (details + icon grid): painting, hit testing,
 * selection, rubber band, column resize / sort, scrolling and keyboard navigation.
 *
 * @author 张晓达
 */
class FmList {
    FmFolder f;
    ArrayList<FmCol> colsNormal;
    ArrayList<FmCol> colsPc;
    ArrayList<FmCol> colsSearch;
    ArrayList<FmCol> cols;
    int x;
    int y;
    int w;
    int h;
    /* 0 details, 1 icon grid */
    int mode;
    /* effective mode: 0 details, 1 icon grid, 2 tiles (This PC / Home) */
    int emode;
    int scroll;
    int hover;
    int hotCol;
    int focusVisible;
    int mx;
    int my;
    /* mouse interaction: 0 none, 1 item press, 2 item drag, 3 rubber band, 4 column resize, 5 header press, 6 scrollbar */
    int dragMode;
    int downX;
    int downY;
    int downIndex;
    int downCtrl;
    int downShift;
    int pendingCollapse;
    int dragCol;
    int dragColW;
    int hdrPress;
    int bandX0;
    int bandY0;
    int bandX1;
    int bandY1;
    int thumbGrab;
    int[] baseSel;
    int dropTarget;
    int hoverBar;
    /* type-ahead */
    String typed;
    int typedTick;
    /* last computed editor rectangle for inline rename */
    int rx;
    int ry;
    int rw;
    int rh;

    FmList(FmFolder folder) {
        f = folder;
        colsNormal = new ArrayList<FmCol>();
        colsNormal.add(new FmCol(0, "名称", 320, 0));
        colsNormal.add(new FmCol(1, "修改日期", 180, 0));
        colsNormal.add(new FmCol(2, "类型", 160, 0));
        colsNormal.add(new FmCol(3, "大小", 100, 1));
        colsPc = new ArrayList<FmCol>();
        colsPc.add(new FmCol(0, "名称", 300, 0));
        colsPc.add(new FmCol(2, "类型", 170, 0));
        colsPc.add(new FmCol(5, "总大小", 130, 1));
        colsPc.add(new FmCol(6, "可用空间", 130, 1));
        colsSearch = new ArrayList<FmCol>();
        colsSearch.add(new FmCol(0, "名称", 280, 0));
        colsSearch.add(new FmCol(1, "修改日期", 160, 0));
        colsSearch.add(new FmCol(2, "类型", 130, 0));
        colsSearch.add(new FmCol(3, "大小", 90, 1));
        colsSearch.add(new FmCol(4, "文件夹", 280, 0));
        cols = colsNormal;
        x = 0;
        y = 0;
        w = 600;
        h = 400;
        mode = 0;
        emode = 0;
        scroll = 0;
        hover = -1;
        hotCol = -1;
        focusVisible = 0;
        mx = -100;
        my = -100;
        dragMode = 0;
        downIndex = -1;
        dropTarget = -1;
        hdrPress = -1;
        typed = "";
        typedTick = 0;
        baseSel = new int[1];
    }

    void configure() {
        emode = mode;
        if (mode == 0 && (f.path.equals("::pc") || f.path.equals("::home"))) {
            emode = 2;
        }
        if (f.path.equals("::pc") || f.path.equals("::home")) {
            cols = colsPc;
        } else if (f.isSearch() != 0) {
            cols = colsSearch;
        } else {
            cols = colsNormal;
        }
    }

    /* ----------------------------------------------------------------- geometry */
    int headerH() {
        if (emode == 0) {
            return Ft.HDR_H;
        }
        return 0;
    }

    int viewH() {
        return h - headerH();
    }

    int gridCols() {
        int c = (w - 16) / Ft.GRID_W;
        if (c < 1) {
            c = 1;
        }
        return c;
    }

    int contentH() {
        int n = f.rows.size();
        if (emode == 0) {
            return n * Ft.ROW_H + 8;
        }
        if (emode == 2) {
            int nd = n - nFolders();
            int tc = tileCols();
            int total = sec2Top();
            if (nd > 0) {
                total = total + 36 + ((nd + tc - 1) / tc) * (Ft.TILE_H + 8);
            }
            return total + 8;
        }
        int c = gridCols();
        int rowsN = (n + c - 1) / c;
        return rowsN * Ft.GRID_H + 16;
    }

    /* ---- tile layout (This PC / Home): section 1 = known folders, section 2 = drives */
    int nFolders() {
        int k = 0;
        int i = 0;
        while (i < f.rows.size()) {
            if (f.rows.get(i).drive == 2) {
                k = k + 1;
            }
            i = i + 1;
        }
        return k;
    }

    int tileCols() {
        int c = (w - 24) / (Ft.TILE_W + 8);
        if (c < 1) {
            c = 1;
        }
        return c;
    }

    int sec2Top() {
        int nf = nFolders();
        if (nf == 0) {
            return 8;
        }
        int tc = tileCols();
        return 8 + 36 + ((nf + tc - 1) / tc) * (Ft.TILE_H + 8) + 8;
    }

    int tileTop(int i) {
        int nf = nFolders();
        int tc = tileCols();
        if (i < nf) {
            return y + 8 + 36 + (i / tc) * (Ft.TILE_H + 8) - scroll;
        }
        int j = i - nf;
        return y + sec2Top() + 36 + (j / tc) * (Ft.TILE_H + 8) - scroll;
    }

    int tileLeft(int i) {
        int nf = nFolders();
        int tc = tileCols();
        int idx = i;
        if (i >= nf) {
            idx = i - nf;
        }
        return x + 12 + (idx % tc) * (Ft.TILE_W + 8);
    }

    int tileAt(int px, int py) {
        int n = f.rows.size();
        int nf = nFolders();
        int tc = tileCols();
        int rx0 = px - x - 12;
        if (rx0 < 0) {
            return -1;
        }
        int col = rx0 / (Ft.TILE_W + 8);
        if (col >= tc || rx0 % (Ft.TILE_W + 8) >= Ft.TILE_W) {
            return -1;
        }
        int rel = py - y + scroll;
        int pitch = Ft.TILE_H + 8;
        if (nf > 0 && rel >= 44 && rel < 44 + ((nf + tc - 1) / tc) * pitch) {
            int r = (rel - 44) / pitch;
            if ((rel - 44) % pitch >= Ft.TILE_H) {
                return -1;
            }
            int idx = r * tc + col;
            if (idx < nf) {
                return idx;
            }
            return -1;
        }
        int nd = n - nf;
        int s2 = sec2Top();
        if (nd > 0 && rel >= s2 + 36 && rel < s2 + 36 + ((nd + tc - 1) / tc) * pitch) {
            int r = (rel - s2 - 36) / pitch;
            if ((rel - s2 - 36) % pitch >= Ft.TILE_H) {
                return -1;
            }
            int idx = nf + r * tc + col;
            if (idx < n) {
                return idx;
            }
        }
        return -1;
    }

    int maxScroll() {
        int m = contentH() - viewH();
        if (m < 0) {
            return 0;
        }
        return m;
    }

    void clampScroll() {
        int m = maxScroll();
        if (scroll > m) {
            scroll = m;
        }
        if (scroll < 0) {
            scroll = 0;
        }
    }

    int totalColsW() {
        int t = 0;
        int i = 0;
        while (i < cols.size()) {
            t = t + cols.get(i).w;
            i = i + 1;
        }
        return t;
    }

    /** Rectangle of row / cell i in view coordinates -> rx,ry,rw,rh fields are NOT touched. */
    int rowTop(int i) {
        if (emode == 0) {
            return y + headerH() + i * Ft.ROW_H - scroll;
        }
        if (emode == 2) {
            return tileTop(i);
        }
        int c = gridCols();
        return y + 8 + (i / c) * Ft.GRID_H - scroll;
    }

    int rowLeft(int i) {
        if (emode == 0) {
            return x;
        }
        if (emode == 2) {
            return tileLeft(i);
        }
        int c = gridCols();
        return x + 8 + (i % c) * Ft.GRID_W;
    }

    int itemW() {
        if (emode == 0) {
            return w;
        }
        if (emode == 2) {
            return Ft.TILE_W;
        }
        return Ft.GRID_W;
    }

    int itemH() {
        if (emode == 0) {
            return Ft.ROW_H;
        }
        if (emode == 2) {
            return Ft.TILE_H;
        }
        return Ft.GRID_H;
    }

    /** Item index under (px,py) or -1. */
    int itemAt(int px, int py) {
        if (px < x || px >= x + w || py < y + headerH() || py >= y + h) {
            return -1;
        }
        int n = f.rows.size();
        if (emode == 0) {
            int i = (py - y - headerH() + scroll) / Ft.ROW_H;
            if (py - y - headerH() + scroll < 0) {
                return -1;
            }
            if (i >= 0 && i < n) {
                return i;
            }
            return -1;
        }
        if (emode == 2) {
            return tileAt(px, py);
        }
        int c = gridCols();
        int rel = px - x - 8;
        if (rel < 0) {
            return -1;
        }
        int col = rel / Ft.GRID_W;
        if (col >= c) {
            return -1;
        }
        int ry0 = py - y - 8 + scroll;
        if (ry0 < 0) {
            return -1;
        }
        int row = ry0 / Ft.GRID_H;
        int i = row * c + col;
        if (i < 0 || i >= n) {
            return -1;
        }
        return i;
    }

    int colAt(int px) {
        int cx = x;
        int i = 0;
        while (i < cols.size()) {
            int cw = cols.get(i).w;
            if (px >= cx && px < cx + cw) {
                return i;
            }
            cx = cx + cw;
            i = i + 1;
        }
        return -1;
    }

    /** Column whose right edge is within 4 px of px (header), else -1. */
    int dividerAt(int px) {
        int cx = x;
        int i = 0;
        while (i < cols.size()) {
            cx = cx + cols.get(i).w;
            if (px >= cx - 4 && px <= cx + 3) {
                return i;
            }
            i = i + 1;
        }
        return -1;
    }

    void ensureVisible(int i) {
        if (i < 0 || i >= f.rows.size()) {
            return;
        }
        int top;
        int bottom;
        if (emode == 0) {
            top = i * Ft.ROW_H;
            bottom = top + Ft.ROW_H;
        } else if (emode == 2) {
            top = tileTop(i) - y + scroll;
            bottom = top + Ft.TILE_H + 8;
        } else {
            int c = gridCols();
            top = (i / c) * Ft.GRID_H;
            bottom = top + Ft.GRID_H + 8;
        }
        int vh = viewH();
        if (top < scroll) {
            scroll = top;
        } else if (bottom + 8 > scroll + vh) {
            scroll = bottom + 8 - vh;
        }
        clampScroll();
    }

    /** Editor rectangle for the name of item i (fills rx, ry, rw, rh). */
    void nameRect(int i) {
        if (emode == 0) {
            rx = x + 36;
            ry = rowTop(i) + 2;
            rw = cols.get(0).w - 44;
            rh = Ft.ROW_H - 4;
        } else {
            rx = rowLeft(i) + 2;
            ry = rowTop(i) + 46;
            rw = Ft.GRID_W - 4;
            rh = 24;
        }
    }

    /* ------------------------------------------------------------------- paint */
    static String iconKey(FmEntry e) {
        if (e.drive != 0) {
            return e.full;
        }
        if (e.isDir != 0) {
            return "*dir";
        }
        String x = e.ext;
        if (x.equals("exe") || x.equals("lnk") || x.equals("ico") || x.equals("scr") || x.equals("url")
                || x.equals("msi") || x.equals("cur") || x.equals("ani")) {
            return e.full;
        }
        if (x.length() == 0) {
            return "*.noextensionfile";
        }
        return "*." + x;
    }

    String cellText(FmEntry e, int colId) {
        if (colId == 0) {
            return FmApp.displayName(e);
        }
        if (colId == 1) {
            return e.mtimeText;
        }
        if (colId == 2) {
            return e.type;
        }
        if (colId == 3) {
            if (e.isDir != 0) {
                return "";
            }
            return FmFmt.sizeKb(e.size);
        }
        if (colId == 4) {
            return e.dir;
        }
        if (colId == 5) {
            if (e.total < 0) {
                return "";
            }
            return FmFmt.sizeHuman(e.total);
        }
        if (colId == 6) {
            if (e.free < 0) {
                return "";
            }
            return FmFmt.sizeHuman(e.free);
        }
        return "";
    }

    void paint() {
        clampScroll();
        int hh = headerH();
        /* rows */
        Gfx.clip(x, y + hh, w, h - hh);
        int n = f.rows.size();
        if (emode == 0) {
            int first = scroll / Ft.ROW_H;
            if (first < 0) {
                first = 0;
            }
            int last = (scroll + viewH()) / Ft.ROW_H + 1;
            if (last >= n) {
                last = n - 1;
            }
            int i = first;
            while (i <= last) {
                paintRow(i);
                i = i + 1;
            }
        } else if (emode == 2) {
            paintTiles();
        } else {
            int c = gridCols();
            int first = (scroll / Ft.GRID_H) * c;
            if (first < 0) {
                first = 0;
            }
            int last = ((scroll + viewH()) / Ft.GRID_H + 1) * c;
            if (last >= n) {
                last = n - 1;
            }
            int i = first;
            while (i <= last) {
                paintCell(i);
                i = i + 1;
            }
        }
        if (n == 0) {
            String msg = "此文件夹为空。";
            if (f.err != 0) {
                msg = "无法访问此文件夹(错误 " + String.valueOf(f.err) + ")。";
            } else if (f.isSearch() != 0) {
                if (FmApp.searching != 0) {
                    msg = "正在搜索…";
                } else {
                    msg = "没有与搜索条件匹配的项。";
                }
            } else if (f.path.length() == 0) {
                msg = "";
            }
            Gfx.text(msg, x, y + hh + 24, w, 24, Ft.sub, Ft.fBody, 1 + 4);
        }
        if (dragMode == 3) {
            paintBand();
        }
        Gfx.unclip();
        paintScrollbar();
        if (emode == 0) {
            paintHeader();
        }
    }

    void paintRow(int i) {
        FmEntry e = f.rows.get(i);
        int ry0 = rowTop(i);
        int selected = e.selected;
        int hot = 0;
        if (i == hover && dragMode != 3) {
            hot = 1;
        }
        if (i == dropTarget) {
            Gfx.round(x + 4, ry0 + 1, w - 8, Ft.ROW_H - 2, 4, Ft.rowSelHover);
        } else if (selected != 0) {
            if (hot != 0) {
                Gfx.round(x + 4, ry0 + 1, w - 8, Ft.ROW_H - 2, 4, Ft.rowSelHover);
            } else {
                Gfx.round(x + 4, ry0 + 1, w - 8, Ft.ROW_H - 2, 4, Ft.rowSel);
            }
        } else if (hot != 0) {
            Gfx.round(x + 4, ry0 + 1, w - 8, Ft.ROW_H - 2, 4, Ft.rowHover);
        }
        if (i == f.focus && focusVisible != 0) {
            Gfx.roundStroke(x + 4, ry0 + 1, w - 8, Ft.ROW_H - 2, 4, 0, Ft.text, 10);
        }
        int tc = Ft.text;
        if (e.hidden() != 0 || FmApp.isCut(e) != 0) {
            tc = Ft.disabled;
        }
        int cx = x;
        int c = 0;
        while (c < cols.size()) {
            FmCol col = cols.get(c);
            if (cx >= x + w) {
                break;
            }
            if (col.id == 0) {
                Gfx.icon(iconKey(e), cx + 14, ry0 + (Ft.ROW_H - 16) / 2, 16);
                Gfx.text(cellText(e, 0), cx + 40, ry0, col.w - 48, Ft.ROW_H, tc, Ft.fBody, 4 + 8);
            } else {
                String t = cellText(e, col.id);
                if (t.length() > 0) {
                    int col2 = Ft.sub;
                    if (tc == Ft.disabled) {
                        col2 = tc;
                    }
                    if (col.right != 0) {
                        Gfx.text(t, cx + 8, ry0, col.w - 24, Ft.ROW_H, col2, Ft.fBody, 2 + 4 + 8);
                    } else {
                        Gfx.text(t, cx + 12, ry0, col.w - 20, Ft.ROW_H, col2, Ft.fBody, 4 + 8);
                    }
                }
            }
            cx = cx + col.w;
            c = c + 1;
        }
    }

    void paintCell(int i) {
        FmEntry e = f.rows.get(i);
        int cx = rowLeft(i);
        int cy = rowTop(i);
        int hot = 0;
        if (i == hover && dragMode != 3) {
            hot = 1;
        }
        if (i == dropTarget) {
            Gfx.round(cx, cy, Ft.GRID_W - 4, Ft.GRID_H - 4, 4, Ft.rowSelHover);
        } else if (e.selected != 0) {
            if (hot != 0) {
                Gfx.round(cx, cy, Ft.GRID_W - 4, Ft.GRID_H - 4, 4, Ft.rowSelHover);
            } else {
                Gfx.round(cx, cy, Ft.GRID_W - 4, Ft.GRID_H - 4, 4, Ft.rowSel);
            }
        } else if (hot != 0) {
            Gfx.round(cx, cy, Ft.GRID_W - 4, Ft.GRID_H - 4, 4, Ft.rowHover);
        }
        if (i == f.focus && focusVisible != 0) {
            Gfx.roundStroke(cx, cy, Ft.GRID_W - 4, Ft.GRID_H - 4, 4, 0, Ft.text, 10);
        }
        Gfx.icon(iconKey(e), cx + (Ft.GRID_W - 4 - 32) / 2, cy + 8, 32);
        int tc = Ft.text;
        if (e.hidden() != 0 || FmApp.isCut(e) != 0) {
            tc = Ft.disabled;
        }
        Gfx.text(cellText(e, 0), cx + 3, cy + 46, Ft.GRID_W - 10, 36, tc, Ft.fBody, 1 + 16);
    }

    void paintTiles() {
        int n = f.rows.size();
        int nf = nFolders();
        if (nf > 0) {
            String title = "文件夹";
            if (f.path.equals("::home")) {
                title = "快速访问";
            }
            Gfx.text(title + " (" + nf + ")", x + 16, y + 8 - scroll, w - 32, 36, Ft.text, Ft.fBold, 4);
        }
        if (n - nf > 0) {
            Gfx.text("设备和驱动器 (" + (n - nf) + ")", x + 16, y + sec2Top() - scroll, w - 32, 36, Ft.text, Ft.fBold, 4);
        }
        int i = 0;
        while (i < n) {
            int ty = tileTop(i);
            if (ty + Ft.TILE_H >= y && ty <= y + h) {
                paintTile(i);
            }
            i = i + 1;
        }
    }

    void paintTile(int i) {
        FmEntry e = f.rows.get(i);
        int tx = tileLeft(i);
        int ty = tileTop(i);
        int hot = 0;
        if (i == hover && dragMode != 3) {
            hot = 1;
        }
        if (i == dropTarget) {
            Gfx.round(tx, ty, Ft.TILE_W, Ft.TILE_H, 4, Ft.rowSelHover);
        } else if (e.selected != 0) {
            if (hot != 0) {
                Gfx.round(tx, ty, Ft.TILE_W, Ft.TILE_H, 4, Ft.rowSelHover);
            } else {
                Gfx.round(tx, ty, Ft.TILE_W, Ft.TILE_H, 4, Ft.rowSel);
            }
        } else if (hot != 0) {
            Gfx.round(tx, ty, Ft.TILE_W, Ft.TILE_H, 4, Ft.rowHover);
        }
        if (i == f.focus && focusVisible != 0) {
            Gfx.roundStroke(tx, ty, Ft.TILE_W, Ft.TILE_H, 4, 0, Ft.text, 10);
        }
        Gfx.icon(iconKey(e), tx + 14, ty + (Ft.TILE_H - 32) / 2, 32);
        int tc = Ft.text;
        if (e.hidden() != 0 || FmApp.isCut(e) != 0) {
            tc = Ft.disabled;
        }
        if (e.drive == 1 && e.total > 0 && e.free >= 0) {
            Gfx.text(FmApp.displayName(e), tx + 58, ty + 6, Ft.TILE_W - 66, 20, tc, Ft.fBody, 4 + 8);
            int bw = Ft.TILE_W - 58 - 14;
            int used = (int) (((e.total - e.free) * 1000) / e.total);
            int fillW = (bw * used) / 1000;
            Gfx.round(tx + 58, ty + 28, bw, 8, 2, Ft.barBg);
            if (fillW > 0) {
                int bc = Ft.barFill;
                if (e.free * 10 < e.total) {
                    bc = Ft.barWarn;
                }
                Gfx.round(tx + 58, ty + 28, fillW, 8, 2, bc);
            }
            Gfx.text(FmFmt.sizeHuman(e.free) + " 可用，共 " + FmFmt.sizeHuman(e.total), tx + 58, ty + 38, Ft.TILE_W - 66, 20, Ft.sub, Ft.fSmall, 4 + 8);
        } else {
            Gfx.text(FmApp.displayName(e), tx + 58, ty, Ft.TILE_W - 66, Ft.TILE_H, tc, Ft.fBody, 4 + 8);
        }
    }

    void paintHeader() {
        Gfx.fill(x, y, w, Ft.HDR_H, Ft.panel);
        int cx = x;
        int c = 0;
        while (c < cols.size()) {
            FmCol col = cols.get(c);
            int hot = 0;
            if (my >= y && my < y + Ft.HDR_H && mx >= cx && mx < cx + col.w && dragMode != 4) {
                hot = 1;
            }
            if (hot != 0 || (dragMode == 5 && hdrPress == c)) {
                Gfx.fill(cx, y + 2, col.w, Ft.HDR_H - 4, Ft.hover);
            }
            int tx = cx + 12;
            if (col.id == 0) {
                tx = cx + 40;
            }
            if (col.right != 0) {
                Gfx.text(col.title, cx + 8, y, col.w - 24, Ft.HDR_H, Ft.sub, Ft.fBody, 2 + 4 + 8);
            } else {
                Gfx.text(col.title, tx, y, col.w - (tx - cx) - 8, Ft.HDR_H, Ft.sub, Ft.fBody, 4 + 8);
            }
            if (col.id == f.sortCol && f.path.length() > 0) {
                int gx = cx + col.w / 2 - 6;
                if (col.right != 0) {
                    gx = cx + col.w - 18;
                }
                int cp = 59150;
                if (f.sortAsc == 0) {
                    cp = 59149;
                }
                Gfx.glyph(cp, gx, y - 7, 12, 14, Ft.sub, Ft.fIconXS, 1 + 4);
            }
            cx = cx + col.w;
            if (cx < x + w) {
                Gfx.fill(cx - 1, y + 6, 1, Ft.HDR_H - 12, Ft.line);
            }
            c = c + 1;
        }
        Gfx.fill(x, y + Ft.HDR_H - 1, w, 1, Ft.line);
    }

    void paintBand() {
        int x0 = Math.min(bandX0, bandX1);
        int y0 = Math.min(bandY0 - scroll + y + headerH(), bandY1);
        int x1 = Math.max(bandX0, bandX1);
        int y1 = Math.max(bandY0 - scroll + y + headerH(), bandY1);
        Gfx.roundStroke(x0, y0, x1 - x0 + 1, y1 - y0 + 1, 0, Ft.band, Ft.bandLine, 10);
    }

    int thumbH() {
        int vh = viewH();
        int ch = contentH();
        if (ch <= vh) {
            return 0;
        }
        int th = (vh * vh) / ch;
        if (th < 32) {
            th = 32;
        }
        return th;
    }

    int thumbY() {
        int vh = viewH();
        int th = thumbH();
        int m = maxScroll();
        if (m <= 0) {
            return y + headerH();
        }
        return y + headerH() + ((vh - th) * scroll) / m;
    }

    void paintScrollbar() {
        int th = thumbH();
        if (th == 0) {
            return;
        }
        int tw = 6;
        if (hoverBar != 0 || dragMode == 6) {
            tw = 8;
        }
        int ty = thumbY();
        int col = Ft.thumb;
        if (hoverBar != 0 || dragMode == 6) {
            col = Ft.thumbHover;
        }
        Gfx.round(x + w - tw - 4, ty, tw, th, tw / 2, col);
    }

    /* -------------------------------------------------------------------- mouse */
    void onLeave() {
        mx = -100;
        my = -100;
        hover = -1;
        hoverBar = 0;
    }

    void onWheel(int delta) {
        int step = (delta * 56) / 120;
        if (emode == 1) {
            step = (delta * Ft.GRID_H) / 240;
        }
        scroll = scroll - step;
        clampScroll();
    }

    int inScrollbar(int px, int py) {
        if (thumbH() == 0) {
            return 0;
        }
        if (px >= x + w - 14 && px < x + w && py >= y + headerH() && py < y + h) {
            return 1;
        }
        return 0;
    }

    /** Applies a plain / ctrl / shift click on item i. */
    void clickSelect(int i, int ctrl, int shift) {
        FmEntry e = f.rows.get(i);
        if (shift != 0 && f.anchor >= 0) {
            f.selectRange(f.anchor, i, ctrl);
            f.setFocus(i);
        } else if (ctrl != 0) {
            if (e.selected != 0) {
                e.selected = 0;
            } else {
                e.selected = 1;
            }
            f.setFocus(i);
            f.anchor = i;
            f.anchorEntry = e;
        } else {
            f.selectOnly(i);
        }
    }

    /** Returns 1 when the event changed something that needs a repaint. */
    int onMove(int px, int py, int btn) {
        mx = px;
        my = py;
        int old = hover;
        int oldBar = hoverBar;
        hoverBar = inScrollbar(px, py);
        if (dragMode == 6) {
            int vh = viewH();
            int th = thumbH();
            int m = maxScroll();
            int span = vh - th;
            if (span > 0) {
                scroll = ((py - thumbGrab - y - headerH()) * m) / span;
                clampScroll();
            }
            return 1;
        }
        if (dragMode == 4) {
            FmCol c = cols.get(dragCol);
            int nw = dragColW + (px - downX);
            if (nw < c.minW) {
                nw = c.minW;
            }
            c.w = nw;
            return 1;
        }
        if (dragMode == 5) {
            return 1;
        }
        if (dragMode == 1) {
            int dx = px - downX;
            int dy = py - downY;
            if (dx * dx + dy * dy >= 36) {
                dragMode = 2;
                pendingCollapse = 0;
            }
            return 1;
        }
        if (dragMode == 2) {
            dropTarget = -1;
            int t = itemAt(px, py);
            if (t >= 0) {
                FmEntry te = f.rows.get(t);
                if (te.isDir != 0 && te.selected == 0) {
                    dropTarget = t;
                }
            }
            return 1;
        }
        if (dragMode == 3) {
            bandX1 = px;
            bandY1 = py;
            updateBand();
            /* edge auto scroll */
            if (py < y + headerH() + 8) {
                scroll = scroll - 24;
            } else if (py > y + h - 8) {
                scroll = scroll + 24;
            }
            clampScroll();
            return 1;
        }
        hover = itemAt(px, py);
        if (emode == 0 && py >= y && py < y + Ft.HDR_H) {
            hotCol = colAt(px);
            if (dividerAt(px) >= 0) {
                Gfx.cursor(2);
            }
        } else {
            hotCol = -1;
        }
        if (old != hover || oldBar != hoverBar || emode == 0) {
            return 1;
        }
        return 0;
    }

    void updateBand() {
        int n = f.rows.size();
        int x0 = Math.min(bandX0, bandX1);
        int x1 = Math.max(bandX0, bandX1);
        int cy0 = Math.min(bandY0, bandY1 - y - headerH() + scroll);
        int cy1 = Math.max(bandY0, bandY1 - y - headerH() + scroll);
        int i = 0;
        while (i < n) {
            FmEntry e = f.rows.get(i);
            int hit = 0;
            if (emode == 0) {
                int t = i * Ft.ROW_H;
                if (t < cy1 && t + Ft.ROW_H > cy0 && x1 > x && x0 < x + w) {
                    hit = 1;
                }
            } else {
                int gx = rowLeft(i);
                int gy = rowTop(i) - y + scroll - headerH();
                if (gy < cy1 && gy + itemH() > cy0 && gx < x1 && gx + itemW() > x0) {
                    hit = 1;
                }
            }
            int base = 0;
            if (i < baseSel.length) {
                base = baseSel[i];
            }
            if (hit != 0 || base != 0) {
                e.selected = 1;
            } else {
                e.selected = 0;
            }
            i = i + 1;
        }
    }

    /**
     * Mouse press. Returns: 0 nothing special, 1 repaint, 2 context menu on item (selection already set),
     * 3 context menu on empty space, 4 sort column requested (FmApp.pendingSort), 5 selection changed.
     */
    int onDown(int px, int py, int btn, int ctrl, int shift) {
        mx = px;
        my = py;
        downX = px;
        downY = py;
        downIndex = -1;
        downCtrl = ctrl;
        downShift = shift;
        focusVisible = 0;
        if (inScrollbar(px, py) != 0 && btn == 1) {
            int ty = thumbY();
            int th = thumbH();
            if (py >= ty && py < ty + th) {
                thumbGrab = py - ty;
            } else {
                thumbGrab = th / 2;
                int span = viewH() - th;
                if (span > 0) {
                    scroll = ((py - thumbGrab - y - headerH()) * maxScroll()) / span;
                    clampScroll();
                }
            }
            dragMode = 6;
            return 1;
        }
        if (emode == 0 && py >= y && py < y + Ft.HDR_H) {
            if (btn != 1) {
                return 0;
            }
            int dv = dividerAt(px);
            if (dv >= 0) {
                dragMode = 4;
                dragCol = dv;
                dragColW = cols.get(dv).w;
                return 1;
            }
            hdrPress = colAt(px);
            if (hdrPress >= 0) {
                dragMode = 5;
            }
            return 1;
        }
        int i = itemAt(px, py);
        if (i >= 0) {
            downIndex = i;
            FmEntry e = f.rows.get(i);
            if (btn == 2) {
                if (e.selected == 0) {
                    f.selectOnly(i);
                } else {
                    f.setFocus(i);
                }
                return 2;
            }
            if (btn != 1) {
                return 0;
            }
            if (e.selected != 0 && ctrl == 0 && shift == 0) {
                /* keep the multi-selection so it can be dragged; collapse on release */
                pendingCollapse = 1;
                f.setFocus(i);
                f.anchor = i;
                f.anchorEntry = e;
                dragMode = 1;
                return 1;
            }
            clickSelect(i, ctrl, shift);
            dragMode = 1;
            pendingCollapse = 0;
            return 5;
        }
        if (btn == 2) {
            f.clearSel();
            return 3;
        }
        if (btn != 1) {
            return 0;
        }
        /* empty space: start a rubber band */
        if (ctrl == 0 && shift == 0) {
            f.clearSel();
        }
        int n = f.rows.size();
        baseSel = new int[n + 1];
        int k = 0;
        while (k < n) {
            if (f.rows.get(k).selected != 0) {
                baseSel[k] = 1;
            }
            k = k + 1;
        }
        bandX0 = px;
        bandY0 = py - y - headerH() + scroll;
        bandX1 = px;
        bandY1 = py;
        dragMode = 3;
        return 5;
    }

    /** Mouse release. Returns 0 nothing, 1 repaint, 4 sort, 6 drop (dropTarget / downIndex valid), 5 selection changed. */
    int onUp(int px, int py, int btn) {
        int r = 1;
        int dm = dragMode;
        dragMode = 0;
        if (dm == 6) {
            return 1;
        }
        if (dm == 4) {
            return 1;
        }
        if (dm == 5) {
            int c = colAt(px);
            if (c >= 0 && c == hdrPress && py >= y && py < y + Ft.HDR_H) {
                FmApp.pendingSort = cols.get(c).id;
                hdrPress = -1;
                return 4;
            }
            hdrPress = -1;
            return 1;
        }
        if (dm == 1) {
            if (pendingCollapse != 0 && downIndex >= 0) {
                f.selectOnly(downIndex);
                pendingCollapse = 0;
                return 5;
            }
            return 1;
        }
        if (dm == 2) {
            return 6;
        }
        if (dm == 3) {
            return 5;
        }
        return r;
    }

    void cancelDrag() {
        dragMode = 0;
        dropTarget = -1;
        pendingCollapse = 0;
    }

    /* ----------------------------------------------------------------- keyboard */
    void moveFocus(int ni, int shift, int ctrl) {
        int n = f.rows.size();
        if (n == 0) {
            return;
        }
        if (ni < 0) {
            ni = 0;
        }
        if (ni >= n) {
            ni = n - 1;
        }
        if (shift != 0) {
            int a = f.anchor;
            if (a < 0) {
                a = 0;
                if (f.focus >= 0) {
                    a = f.focus;
                }
            }
            f.selectRange(a, ni, 0);
            f.setFocus(ni);
            f.anchor = a;
            f.anchorEntry = f.rows.get(a);
        } else if (ctrl != 0) {
            f.setFocus(ni);
        } else {
            f.selectOnly(ni);
        }
        focusVisible = 1;
        ensureVisible(ni);
    }

    /** Tile layout: the tile visually above (dir -1) / below (dir 1) item cur, or cur if none. */
    int vertTarget(int cur, int dir) {
        int n = f.rows.size();
        int ct = tileTop(cur);
        int cl = tileLeft(cur);
        int best = cur;
        int bestDy = 1000000;
        int bestDx = 1000000;
        int i = 0;
        while (i < n) {
            int dy = (tileTop(i) - ct) * dir;
            if (dy > 0) {
                int dx = Math.abs(tileLeft(i) - cl);
                if (dy < bestDy || (dy == bestDy && dx < bestDx)) {
                    best = i;
                    bestDy = dy;
                    bestDx = dx;
                }
            }
            i = i + 1;
        }
        return best;
    }

    /** Returns 1 when the key was consumed. */
    int onKey(int vk, int mods) {
        int shift = mods % 2;
        int ctrl = (mods / 2) % 2;
        int alt = (mods / 4) % 2;
        int n = f.rows.size();
        if (alt != 0) {
            return 0;
        }
        int cur = f.focus;
        int per = 1;
        int page = 1;
        if (emode == 0) {
            page = viewH() / Ft.ROW_H - 1;
        } else if (emode == 2) {
            per = tileCols();
            page = (viewH() / (Ft.TILE_H + 8)) * per;
        } else {
            per = gridCols();
            page = (viewH() / Ft.GRID_H) * per;
        }
        if (page < 1) {
            page = 1;
        }
        if (vk == 38) {
            if (cur < 0) {
                moveFocus(n - 1, shift, ctrl);
            } else if (emode == 0) {
                moveFocus(cur - 1, shift, ctrl);
            } else if (emode == 2) {
                moveFocus(vertTarget(cur, -1), shift, ctrl);
            } else {
                moveFocus(cur - per, shift, ctrl);
            }
            return 1;
        }
        if (vk == 40) {
            if (cur < 0) {
                moveFocus(0, shift, ctrl);
            } else if (emode == 0) {
                moveFocus(cur + 1, shift, ctrl);
            } else if (emode == 2) {
                moveFocus(vertTarget(cur, 1), shift, ctrl);
            } else {
                moveFocus(cur + per, shift, ctrl);
            }
            return 1;
        }
        if (vk == 37 && emode != 0) {
            moveFocus(cur - 1, shift, ctrl);
            return 1;
        }
        if (vk == 39 && emode != 0) {
            moveFocus(cur + 1, shift, ctrl);
            return 1;
        }
        if (vk == 36) {
            moveFocus(0, shift, ctrl);
            return 1;
        }
        if (vk == 35) {
            moveFocus(n - 1, shift, ctrl);
            return 1;
        }
        if (vk == 33) {
            moveFocus(cur - page, shift, ctrl);
            return 1;
        }
        if (vk == 34) {
            moveFocus(cur + page, shift, ctrl);
            return 1;
        }
        if (vk == 32 && ctrl != 0 && cur >= 0) {
            FmEntry e = f.rows.get(cur);
            if (e.selected != 0) {
                e.selected = 0;
            } else {
                e.selected = 1;
            }
            focusVisible = 1;
            return 1;
        }
        return 0;
    }

    /** Type-ahead search by name prefix; tick is the app tick counter (50 ms units). */
    int typeAhead(int ch, int tick) {
        int n = f.rows.size();
        if (n == 0) {
            return 0;
        }
        if (tick - typedTick > 18) {
            typed = "";
        }
        typedTick = tick;
        int lc = ch;
        if (lc >= 65 && lc <= 90) {
            lc = lc + 32;
        }
        typed = typed + charStr(lc);
        int start = f.focus;
        if (typed.length() == 1) {
            start = f.focus + 1;
        }
        if (start < 0) {
            start = 0;
        }
        int k = 0;
        while (k < n) {
            int i = (start + k) % n;
            if (f.rows.get(i).key.startsWith(typed)) {
                moveFocus(i, 0, 0);
                return 1;
            }
            k = k + 1;
        }
        return 0;
    }

    static String charStr(int ch) {
        byte[] b = new byte[1];
        b[0] = (byte) ch;
        return String.fromBytes(b);
    }
}
