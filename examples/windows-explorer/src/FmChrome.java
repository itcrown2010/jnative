/**
 * JNative Windows Explorer - window chrome painting: navigation row, address bar, command bar,
 * navigation pane and status bar. Registers hit rectangles in FmHit while painting.
 *
 * @author 张晓达
 */
class FmChrome {
    static int mx = -100;
    static int my = -100;
    /* breadcrumb paths of the last paint, index = FmHit data */
    static String[] crumbPath = new String[32];
    static int crumbN = 0;
    static int crumbOverflowPath = 0;
    static int navScroll = 0;
    static int navDrop = -1;

    static int hot(int x, int y, int w, int h) {
        if (mx >= x && mx < x + w && my >= y && my < y + h) {
            return 1;
        }
        return 0;
    }

    /** Square glyph button (navigation row). */
    static void iconButton(int x, int y, int size, int cp, int enabled, int hitId, int data) {
        int col = Ft.text;
        if (enabled == 0) {
            col = Ft.disabled;
        } else if (hot(x, y, size, size) != 0) {
            Gfx.round(x, y, size, size, 4, Ft.hover);
        }
        Gfx.glyph(cp, x, y, size, size, col, Ft.fIcon, 1 + 4);
        if (enabled != 0) {
            FmHit.add(x, y, size, size, hitId, data);
        }
    }

    /** Command-bar button: [glyph] [label] [chevron]; returns its width. */
    static int cmdButton(int x, int y, int h, int cp, String label, int chevron, int enabled, int hitId) {
        int tw = 0;
        if (label.length() > 0) {
            tw = Gfx.textWidth(Ft.fBody, label);
        }
        int w = 20;
        if (cp > 0) {
            w = w + 16;
        }
        if (label.length() > 0) {
            w = w + tw;
            if (cp > 0) {
                w = w + 8;
            }
        }
        if (chevron != 0) {
            w = w + 18;
        }
        int col = Ft.text;
        if (enabled == 0) {
            col = Ft.disabled;
        }
        if (enabled != 0 && hot(x, y, w, h) != 0) {
            Gfx.round(x, y, w, h, 4, Ft.hover);
        }
        int cx = x + 10;
        if (cp > 0) {
            Gfx.glyph(cp, cx, y, 16, h, col, Ft.fIcon, 1 + 4);
            cx = cx + 24;
        }
        if (label.length() > 0) {
            Gfx.text(label, cx, y, tw + 4, h, col, Ft.fBody, 4 + 8);
            cx = cx + tw + 6;
        }
        if (chevron != 0) {
            Gfx.glyph(59149, cx, y, 10, h, col, Ft.fIconXS, 1 + 4);
        }
        if (enabled != 0) {
            FmHit.add(x, y, w, h, hitId, 0);
        }
        return w;
    }

    static void paintNavRow(int W) {
        int y = 8;
        int canB = 0;
        if (FmApp.hist.canBack()) {
            canB = 1;
        }
        int canF = 0;
        if (FmApp.hist.canForward()) {
            canF = 1;
        }
        iconButton(8, y, 32, 59179, canB, FmId.BACK, 0);
        iconButton(44, y, 32, 59178, canF, FmId.FORWARD, 0);
        int canUp = 0;
        if (FmApp.parentOfCurrent().length() > 0) {
            canUp = 1;
        }
        iconButton(80, y, 32, 59210, canUp, FmId.UP, 0);
        iconButton(116, y, 32, 59180, 1, FmId.REFRESH, 0);
        int sw = W / 4;
        if (sw < 190) {
            sw = 190;
        }
        if (sw > 320) {
            sw = 320;
        }
        int sx = W - 12 - sw;
        int ax = 158;
        int aw = sx - 10 - ax;
        if (aw < 120) {
            aw = 120;
        }
        FmApp.addrX = ax;
        FmApp.addrW = aw;
        paintAddress(ax, y, aw, 32);
        /* search box frame (the native TextField sits inside) */
        Gfx.roundStroke(sx, y, sw, 32, 6, Ft.editBg, Ft.editLine, 10);
        if (FmApp.sbox.hasFocus()) {
            Gfx.fill(sx + 3, y + 31, sw - 6, 2, Ft.accent);
        } else {
            Gfx.fill(sx + 3, y + 31, sw - 6, 1, Ft.editLineBottom);
        }
        Gfx.glyph(59169, sx + sw - 30, y, 24, 32, Ft.sub, Ft.fIconS, 1 + 4);
        int ex = sx + 10;
        int ew = sw - 44;
        if (FmApp.sbX != ex || FmApp.sbW != ew || FmApp.sbY != y + 7) {
            FmApp.sbX = ex;
            FmApp.sbW = ew;
            FmApp.sbY = y + 7;
            FmApp.sbox.setBounds(ex, y + 7, ew, 18);
        }
    }

    static void paintAddress(int x, int y, int w, int h) {
        Gfx.roundStroke(x, y, w, h, 6, Ft.editBg, Ft.editLine, 10);
        if (FmApp.editingAddr != 0) {
            Gfx.fill(x + 3, y + h - 1, w - 6, 2, Ft.accent);
            return;
        }
        Gfx.fill(x + 3, y + h - 1, w - 6, 1, Ft.editLineBottom);
        FmHit.add(x, y, w, h, FmId.ADDR_BG, 0);
        /* current location icon */
        String ik = FmApp.locationIconKey();
        if (ik.length() > 0) {
            Gfx.icon(ik, x + 10, y + 8, 16);
        } else {
            Gfx.glyph(59407, x + 8, y, 20, h, Ft.text, Ft.fIconS, 1 + 4);
        }
        /* breadcrumb segments */
        ArrayList<String> labels = new ArrayList<String>();
        ArrayList<String> paths = new ArrayList<String>();
        FmApp.buildCrumbs(labels, paths);
        int n = labels.size();
        int avail = w - 38 - 10;
        int[] tw = new int[n + 1];
        int total = 0;
        int i = 0;
        while (i < n) {
            tw[i] = Gfx.textWidth(Ft.fBody, labels.get(i)) + 14;
            total = total + tw[i] + 16;
            i = i + 1;
        }
        int start = 0;
        while (start < n - 1 && total > avail - 28) {
            total = total - tw[start] - 16;
            start = start + 1;
        }
        int cx = x + 36;
        crumbN = 0;
        if (start > 0) {
            Gfx.text("…", cx, y, 22, h, Ft.sub, Ft.fBody, 1 + 4);
            cx = cx + 24;
        }
        i = start;
        while (i < n) {
            String lb = labels.get(i);
            int cw = tw[i];
            int slot = crumbN;
            if (slot < 32) {
                crumbPath[slot] = paths.get(i);
                crumbN = crumbN + 1;
            }
            if (hot(cx, y + 3, cw, h - 6) != 0) {
                Gfx.round(cx, y + 3, cw, h - 6, 4, Ft.hover);
            }
            Gfx.text(lb, cx + 7, y, cw - 8, h, Ft.text, Ft.fBody, 4 + 8);
            FmHit.add(cx, y + 3, cw, h - 6, FmId.CRUMB, slot);
            cx = cx + cw;
            if (i < n - 1 || FmApp.crumbHasKids(paths.get(i)) != 0) {
                if (hot(cx, y + 3, 16, h - 6) != 0) {
                    Gfx.round(cx, y + 3, 16, h - 6, 4, Ft.hover);
                }
                Gfx.glyph(59244, cx, y, 16, h, Ft.sub, Ft.fIconXS, 1 + 4);
                FmHit.add(cx, y + 3, 16, h - 6, FmId.CRUMB_ARROW, slot);
            }
            cx = cx + 16;
            i = i + 1;
        }
    }

    static void paintCommandBar(int W) {
        int y = Ft.NAV_H;
        int h = Ft.CMD_H;
        int by = y + 4;
        int bh = h - 8;
        int x = 8;
        FmFolder f = FmApp.folder;
        int real = f.isReal();
        int sc = f.selCount();
        int canNew = 0;
        if (real != 0) {
            canNew = 1;
        }
        x = x + cmdButton(x, by, bh, 59152, "新建", 1, canNew, FmId.CMD_NEW) + 2;
        Gfx.fill(x + 2, y + 10, 1, h - 20, Ft.line);
        x = x + 8;
        int canOp = 0;
        if (sc > 0) {
            canOp = 1;
        }
        int canPaste = 0;
        if (real != 0 && FmApp.clipState > 0) {
            canPaste = 1;
        }
        int canRen = 0;
        if (sc == 1 && FmApp.selectionIsFileSystem() != 0) {
            canRen = 1;
        }
        int canDel = 0;
        if (canOp != 0 && FmApp.selectionIsFileSystem() != 0) {
            canDel = 1;
        }
        int canCut = canDel;
        x = x + cmdButton(x, by, bh, 59590, "", 0, canCut, FmId.CMD_CUT);
        x = x + cmdButton(x, by, bh, 59592, "", 0, canCut, FmId.CMD_COPY);
        x = x + cmdButton(x, by, bh, 59263, "", 0, canPaste, FmId.CMD_PASTE);
        x = x + cmdButton(x, by, bh, 59564, "", 0, canRen, FmId.CMD_RENAME);
        x = x + cmdButton(x, by, bh, 59213, "", 0, canDel, FmId.CMD_DELETE);
        Gfx.fill(x + 6, y + 10, 1, h - 20, Ft.line);
        x = x + 12;
        int canSort = 0;
        if (f.path.length() > 0 && !f.path.equals("::pc") && !f.path.equals("::home")) {
            canSort = 1;
        }
        x = x + cmdButton(x, by, bh, 59595, "排序", 1, canSort, FmId.CMD_SORT);
        x = x + cmdButton(x, by, bh, 59561, "查看", 1, 1, FmId.CMD_VIEW);
        Gfx.fill(x + 6, y + 10, 1, h - 20, Ft.line);
        x = x + 12;
        cmdButton(x, by, bh, 59154, "", 0, 1, FmId.CMD_MORE);
    }

    /* --------------------------------------------------------------- nav pane */
    static void paintNavPane(int x, int y, int w, int h) {
        FmNavTree t = FmApp.nav;
        int total = t.vis.size() * Ft.NAV_ITEM_H;
        int maxS = total - h + 8;
        if (maxS < 0) {
            maxS = 0;
        }
        if (navScroll > maxS) {
            navScroll = maxS;
        }
        if (navScroll < 0) {
            navScroll = 0;
        }
        Gfx.clip(x, y, w, h);
        String cur = FmApp.folder.path;
        if (FmApp.folder.isSearch() != 0) {
            cur = FmApp.folder.searchRoot;
        }
        int i = 0;
        int iy = y + 4 - navScroll;
        while (i < t.vis.size()) {
            FmNode n = t.vis.get(i);
            int ih = Ft.NAV_ITEM_H;
            if (iy + ih < y || iy > y + h) {
                iy = iy + ih;
                i = i + 1;
                continue;
            }
            if (n.separator != 0) {
                Gfx.fill(x + 12, iy + ih / 2, w - 24, 1, Ft.line);
                iy = iy + ih;
                i = i + 1;
                continue;
            }
            int sel = 0;
            if (n.path.length() > 0 && FmPath.samePath(n.path, cur) != 0) {
                sel = 1;
            }
            int hv = hot(x, iy, w, ih);
            if (i == navDrop) {
                Gfx.round(x + 4, iy + 1, w - 8, ih - 2, 4, Ft.rowSelHover);
            } else if (sel != 0) {
                Gfx.round(x + 4, iy + 1, w - 8, ih - 2, 4, Ft.rowSel);
                Gfx.round(x + 4, iy + 8, 3, ih - 16, 2, Ft.accent);
            } else if (hv != 0) {
                Gfx.round(x + 4, iy + 1, w - 8, ih - 2, 4, Ft.rowHover);
            }
            int ix = x + 10 + n.depth * 14;
            FmHit.add(x, iy, w, ih, FmId.NAV_ITEM, i);
            if (n.mayHaveKids != 0 && n.path.length() > 0) {
                int cp = 59244;
                if (n.expanded != 0) {
                    cp = 59149;
                }
                Gfx.glyph(cp, ix, iy, 14, ih, Ft.sub, Ft.fIconXS, 1 + 4);
                FmHit.add(ix - 2, iy, 18, ih, FmId.NAV_ARROW, i);
            }
            ix = ix + 20;
            if (n.iconPath.length() > 0) {
                Gfx.icon(n.iconPath, ix, iy + (ih - 16) / 2, 16);
            } else if (n.glyph > 0) {
                Gfx.glyph(n.glyph, ix, iy, 16, ih, Ft.accent, Ft.fIcon, 1 + 4);
            } else {
                Gfx.icon("*dir", ix, iy + (ih - 16) / 2, 16);
            }
            Gfx.text(n.label, ix + 24, iy, x + w - ix - 28, ih, Ft.text, Ft.fBody, 4 + 8);
            iy = iy + ih;
            i = i + 1;
        }
        Gfx.unclip();
    }

    /** Index in nav.vis under (px,py) or -1 (the pane starts at y0). */
    static int navIndexAt(int px, int py, int x, int y0, int w) {
        if (px < x || px >= x + w) {
            return -1;
        }
        int rel = py - y0 - 4 + navScroll;
        if (rel < 0) {
            return -1;
        }
        int i = rel / Ft.NAV_ITEM_H;
        if (i >= FmApp.nav.vis.size()) {
            return -1;
        }
        return i;
    }

    /* ------------------------------------------------------------------ status */
    static void paintStatus(int x, int y, int w, int h) {
        FmFolder f = FmApp.folder;
        String s = "";
        int n = f.rows.size();
        if (f.isSearch() != 0) {
            s = "找到 " + FmFmt.plural(n);
            if (FmApp.searching != 0) {
                s = s + "，正在搜索…";
            }
        } else if (f.path.length() > 0) {
            s = FmFmt.plural(n);
        }
        int tx = x + 14;
        int tw = Gfx.textWidth(Ft.fSmall, s);
        Gfx.text(s, tx, y, tw + 8, h, Ft.sub, Ft.fSmall, 4);
        tx = tx + tw + 10;
        int sc = f.selCount();
        if (sc > 0) {
            Gfx.fill(tx, y + 7, 1, h - 14, Ft.line);
            tx = tx + 12;
            String s2 = "已选择 " + FmFmt.plural(sc);
            long bytes = f.selBytes();
            if (bytes >= 0) {
                s2 = s2 + "    " + FmFmt.sizeHuman(bytes);
            }
            Gfx.text(s2, tx, y, 400, h, Ft.sub, Ft.fSmall, 4);
        }
        /* view toggles */
        int bx = x + w - 66;
        int by = y + 2;
        int bh = h - 4;
        paintViewToggle(bx, by, 28, bh, 0, FmApp.list.mode == 0);
        paintViewToggle(bx + 32, by, 28, bh, 1, FmApp.list.mode == 1);
    }

    static void paintViewToggle(int x, int y, int w, int h, int kind, boolean on) {
        if (on) {
            Gfx.round(x, y, w, h, 4, Ft.pressed);
        } else if (hot(x, y, w, h) != 0) {
            Gfx.round(x, y, w, h, 4, Ft.hover);
        }
        int cx = x + w / 2;
        int cy = y + h / 2;
        int col = Ft.text;
        if (kind == 0) {
            int r = 0;
            while (r < 3) {
                Gfx.fill(cx - 7, cy - 5 + r * 5, 3, 2, col);
                Gfx.fill(cx - 2, cy - 5 + r * 5, 9, 2, col);
                r = r + 1;
            }
        } else {
            Gfx.roundStroke(cx - 7, cy - 7, 6, 6, 1, 0, col, 10);
            Gfx.roundStroke(cx + 1, cy - 7, 6, 6, 1, 0, col, 10);
            Gfx.roundStroke(cx - 7, cy + 1, 6, 6, 1, 0, col, 10);
            Gfx.roundStroke(cx + 1, cy + 1, 6, 6, 1, 0, col, 10);
        }
        int hid = FmId.VIEW_GRID;
        if (kind == 0) {
            hid = FmId.VIEW_DETAILS;
        }
        FmHit.add(x, y, w, h, hid, 0);
    }
}
