/**
 * JNative Windows Task Monitor - a Windows 11 style Task Manager.
 * The whole UI is drawn by Java onto a double-buffered canvas (GDI + GDI+) through UiLib, compiled
 * ahead of time by JNative into a native EXE (no Swing / AWT / JRE).
 *
 * @author 张晓达
 */
public class TaskMonitor {
    public static void main(String[] args) {
        TmApp.start();
    }
}

class TmApp {
    static Window win;
    static PaintView cv;
    static TextField search;
    static int page = 0;
    static int navUser = 0;
    static String filter = "";
    static String filterRaw = "";
    /* 0 high, 1 normal, 2 low, 3 paused */
    static int speed = 1;
    static int elevated = 0;
    static TmGrid gProc;
    static TmGrid gDet;
    static TmGrid gSvc;
    static TmGrid gStart;
    static TmGrid gUsers;
    static TmGrid gHist;
    static int W = 1180;
    static int H = 760;
    static int navW = 216;
    static int sbx = -1;
    static int sby = -1;
    static int sbw = -1;
    static int tickN = 0;
    static int themeTick = 0;
    static int ctxPage = 0;
    static String ctxKey = "";
    static int ctxCol = -1;
    static int lastBtn = 0;
    static int svcTick = -100;
    static int sessTick = -100;
    static int sampling = 0;
    static long lastRefreshMs = 0;
    static long lastPaintMs = 0;
    static int paintErrors = 0;

    static void start() {
        Application.setDpiAware(true);
        Application app = new Application();
        TmStore.init();
        elevated = TmNative.isElevated();
        win = new Window("任务管理器", 1180, 760);
        win.setMinSize(900, 600);
        Panel root = new Panel();
        cv = new PaintView();
        root.add(cv);
        search = new TextField("");
        search.setFlat();
        search.setCueBanner("键入要搜索的名称、发布者或 PID");
        root.add(search);
        win.setContent(root);
        initGrids();
        applyTheme();
        cv.setOnPaint(() -> {
            TmApp.paint();
        });
        cv.onMouse((ev) -> {
            TmApp.onMouse(ev);
        });
        win.onKeyDown((ev) -> {
            TmApp.onKey(ev);
        });
        search.onChange((ev) -> {
            TmApp.onSearch();
        });
        win.setResizeListener(() -> {
            TmApp.onResize();
        });
        win.show();
        onResize();
        refresh();
        cv.startTimer(1000, () -> {
            TmApp.tick();
        });
        app.run();
    }

    /* ------------------------------------------------------------------ setup */
    static void initGrids() {
        gProc = new TmGrid(0);
        gProc.hdrH = 49;
        gProc.addCol(Col.NAME, "名称", 200, 0, 1, 0);
        gProc.addCol(Col.STATUS, "状态", 100, 0, 1, 0);
        gProc.addCol(Col.CPU, "CPU", 80, 2, 1, 1);
        gProc.addCol(Col.MEM, "内存", 104, 2, 1, 1);
        gProc.addCol(Col.DISK, "磁盘", 100, 2, 1, 1);
        gProc.addCol(Col.PUB, "发布者", 170, 0, 0, 0);
        gProc.addCol(Col.PID, "PID", 72, 2, 0, 0);
        gProc.addCol(Col.USER, "用户名", 120, 0, 0, 0);
        gProc.addCol(Col.THREADS, "线程数", 76, 2, 0, 1);
        gProc.addCol(Col.HANDLES, "句柄数", 76, 2, 0, 1);
        gProc.addCol(Col.TYPE, "类型", 110, 0, 0, 0);
        gProc.addCol(Col.CMD, "命令行", 320, 0, 0, 0);
        gProc.addCol(Col.PATH, "映像路径名称", 300, 0, 0, 0);

        gDet = new TmGrid(1);
        gDet.hdrH = 38;
        gDet.addCol(Col.NAME, "名称", 200, 0, 1, 0);
        gDet.addCol(Col.PID, "PID", 60, 2, 1, 0);
        gDet.addCol(Col.STATUS, "状态", 84, 0, 1, 0);
        gDet.addCol(Col.USER, "用户名", 108, 0, 1, 0);
        gDet.addCol(Col.CPU, "CPU", 58, 2, 1, 1);
        gDet.addCol(Col.MEM, "内存(工作集)", 112, 2, 1, 1);
        gDet.addCol(Col.ARCH, "体系结构", 82, 0, 1, 0);
        gDet.addCol(Col.DESC, "说明", 190, 0, 1, 0);
        gDet.addCol(Col.SESSION, "会话 ID", 76, 2, 0, 0);
        gDet.addCol(Col.HANDLES, "句柄", 70, 2, 0, 1);
        gDet.addCol(Col.THREADS, "线程", 70, 2, 0, 1);
        gDet.addCol(Col.CPUTIME, "CPU 时间", 90, 2, 0, 1);
        gDet.addCol(Col.PRIO, "基本优先级", 96, 0, 0, 0);
        gDet.addCol(Col.IOTOTAL, "I/O 总量", 100, 2, 0, 1);
        gDet.addCol(Col.CMD, "命令行", 320, 0, 0, 0);
        gDet.addCol(Col.PATH, "映像路径名称", 300, 0, 0, 0);

        gSvc = new TmGrid(2);
        gSvc.hdrH = 38;
        gSvc.addCol(0, "名称", 190, 0, 1, 0);
        gSvc.addCol(1, "PID", 72, 2, 1, 0);
        gSvc.addCol(2, "说明", 260, 0, 1, 0);
        gSvc.addCol(3, "状态", 100, 0, 1, 0);
        gSvc.addCol(4, "组", 150, 0, 1, 0);
        gSvc.addCol(6, "启动类型", 110, 0, 0, 0);
        gSvc.addCol(5, "详细信息", 320, 0, 0, 0);

        gStart = new TmGrid(3);
        gStart.hdrH = 38;
        gStart.addCol(0, "名称", 260, 0, 1, 0);
        gStart.addCol(1, "发布者", 220, 0, 1, 0);
        gStart.addCol(2, "状态", 100, 0, 1, 0);
        gStart.addCol(3, "启动影响", 100, 0, 1, 0);
        gStart.addCol(4, "来源", 200, 0, 0, 0);
        gStart.addCol(5, "命令行", 360, 0, 0, 0);

        gUsers = new TmGrid(4);
        gUsers.hdrH = 49;
        gUsers.addCol(Col.NAME, "用户", 200, 0, 1, 0);
        gUsers.addCol(Col.STATUS, "状态", 110, 0, 1, 0);
        gUsers.addCol(Col.CPU, "CPU", 80, 2, 1, 1);
        gUsers.addCol(Col.MEM, "内存", 104, 2, 1, 1);
        gUsers.addCol(Col.DISK, "磁盘", 100, 2, 1, 1);
        gUsers.addCol(Col.SESSION, "会话 ID", 76, 2, 0, 0);

        gHist = new TmGrid(5);
        gHist.hdrH = 38;
        gHist.addCol(Col.NAME, "名称", 240, 0, 1, 0);
        gHist.addCol(Col.CPUTIME, "CPU 时间", 110, 2, 1, 1);
        gHist.addCol(Col.MEM, "内存(工作集)", 120, 2, 1, 1);
        gHist.addCol(Col.IOTOTAL, "I/O 总量", 110, 2, 1, 1);
        gHist.addCol(Col.THREADS, "线程", 80, 2, 0, 1);
        gHist.sortCol = Col.CPUTIME;
        gHist.sortAsc = 0;
    }

    static int isDark() {
        if (Th.mode == 2) {
            return 1;
        }
        if (Th.mode == 1) {
            return 0;
        }
        if (UiNative.systemDark() == 1) {
            return 1;
        }
        return 0;
    }

    static void applyTheme() {
        int d = isDark();
        Th.apply(d);
        if (d == 0) {
            win.setCaptionColors(238 * 65536 + 244 * 256 + 249, 27 * 65536 + 27 * 256 + 27, false);
        } else {
            win.setCaptionColors(32 * 65536 + 32 * 256 + 32, 255 * 65536 + 255 * 256 + 255, true);
        }
        search.setBackgroundRgba(Th.editBg);
        search.setForegroundRgba(Th.text);
    }

    static TmGrid activeGrid() {
        if (page == 0) {
            return gProc;
        }
        if (page == 2) {
            return gHist;
        }
        if (page == 3) {
            return gStart;
        }
        if (page == 4) {
            return gUsers;
        }
        if (page == 5) {
            return gDet;
        }
        if (page == 6) {
            return gSvc;
        }
        return null;
    }

    /* ----------------------------------------------------------------- refresh */
    static int intervalMs() {
        if (speed == 0) {
            return 500;
        }
        if (speed == 2) {
            return 4000;
        }
        return 1000;
    }

    static void setSpeed(int s) {
        speed = s;
        if (s == 3) {
            cv.stopTimer();
        } else {
            cv.startTimer(intervalMs(), () -> {
                TmApp.tick();
            });
        }
    }

    static void tick() {
        if (speed == 3) {
            return;
        }
        refresh();
    }

    static void refresh() {
        if (sampling != 0) {
            return;
        }
        sampling = 1;
        long t0 = AmNative.monoNs();
        tickN = tickN + 1;
        TmStore.samplePerf();
        int needX = 0;
        if (page == 0 || page == 2 || page == 4 || page == 5) {
            needX = 1;
        }
        TmStore.sampleProcs(needX);
        if ((page == 0 || page == 6) && (tickN - svcTick >= 6 || TmStore.svcs.size() == 0)) {
            TmStore.sampleServices();
            svcTick = tickN;
        }
        if (page == 4 && (tickN - sessTick >= 3 || TmStore.sessions.size() == 0)) {
            TmStore.sampleSessions();
            sessTick = tickN;
        }
        if (page == 3 && TmStore.startupLoaded == 0) {
            TmStore.loadStartup();
        }
        themeTick = themeTick + 1;
        if (Th.mode == 0 && themeTick % 5 == 0) {
            int d = isDark();
            if (d != Th.dark) {
                applyTheme();
            }
        }
        rebuild();
        sampling = 0;
        lastRefreshMs = (AmNative.monoNs() - t0) / 1000000L;
        cv.invalidate();
    }

    static void rebuild() {
        if (page == 0) {
            TmBuild.buildProcesses(gProc);
        } else if (page == 2) {
            TmBuild.buildHistory(gHist);
        } else if (page == 3) {
            TmBuild.buildStartup(gStart);
        } else if (page == 4) {
            TmBuild.buildUsers(gUsers);
        } else if (page == 5) {
            TmBuild.buildDetails(gDet);
        } else if (page == 6) {
            TmBuild.buildServices(gSvc);
        }
    }

    static void gotoPage(int p) {
        if (p == page) {
            return;
        }
        page = p;
        if (page == 3 && TmStore.startupLoaded == 0) {
            TmStore.loadStartup();
        }
        if (page == 6 && TmStore.svcs.size() == 0) {
            TmStore.sampleServices();
        }
        if (page == 4 && TmStore.sessions.size() == 0) {
            TmStore.sampleSessions();
        }
        /* process lists need the extra (procx) data right away */
        if (page == 0 || page == 2 || page == 4 || page == 5) {
            TmStore.sampleProcs(1);
        }
        rebuild();
        cv.invalidate();
    }

    static void onSearch() {
        filterRaw = AmParse.trim(search.getText());
        filter = filterRaw.toLowerCase();
        rebuild();
        cv.invalidate();
    }

    static void onResize() {
        cv.setBounds(0, 0, win.width, win.height);
        cv.invalidate();
    }

    /* ------------------------------------------------------------------- paint */
    static int navCollapsed() {
        if (navUser != 0 || W < 860) {
            return 1;
        }
        return 0;
    }

    static void paint() {
        long pt0 = AmNative.monoNs();
        Th.initFonts();
        TmHit.reset();
        W = win.width;
        H = win.height;
        if (navCollapsed() != 0) {
            navW = Th.NAV_W_COLLAPSED;
        } else {
            navW = Th.NAV_W;
        }
        Gfx.fill(0, 0, W, H, Th.bg);
        paintStrip();
        paintNav();
        int px = navW;
        int py = Th.STRIP_H;
        int pw = W - navW;
        int ph = H - py;
        Gfx.roundStroke(px, py, pw + 16, ph + 16, Th.RADIUS, Th.panel, Th.line, 10);
        paintCmdBar(px, py, pw);
        int cy = py + Th.CMD_H;
        int ch = ph - Th.CMD_H;
        if (page == 1) {
            TmPerf.paint(px, cy, pw, ch);
        } else if (page == 7) {
            TmPerf.paintSettings(px, cy, pw, ch);
        } else {
            TmGrid g = activeGrid();
            updateHeaders();
            g.paint(px, cy, pw, ch);
        }
        lastPaintMs = (AmNative.monoNs() - pt0) / 1000000L;
    }

    static void updateHeaders() {
        TmCol c = gProc.colById(Col.CPU);
        c.big = Fmt.pctWhole(TmStore.cpuTotalX10);
        c = gProc.colById(Col.MEM);
        long memPm = 0;
        if (TmStore.memTotal > 0) {
            memPm = (TmStore.memInUse * 1000L) / TmStore.memTotal;
        }
        c.big = Fmt.pctWhole((int) memPm);
        c = gProc.colById(Col.DISK);
        int act = 0;
        int i = 0;
        while (i < TmStore.disks.size()) {
            if (TmStore.disks.get(i).actX10 > act) {
                act = TmStore.disks.get(i).actX10;
            }
            i = i + 1;
        }
        c.big = Fmt.pctWhole(act);
        gUsers.colById(Col.CPU).big = gProc.colById(Col.CPU).big;
        gUsers.colById(Col.MEM).big = gProc.colById(Col.MEM).big;
        gUsers.colById(Col.DISK).big = gProc.colById(Col.DISK).big;
    }

    static void paintStrip() {
        int sw = Math.min(460, W - 360);
        if (sw < 220) {
            sw = 220;
        }
        int sx = (W - sw) / 2;
        int sy = 6;
        int sh = 32;
        Gfx.roundStroke(sx, sy, sw, sh, 6, Th.editBg, Th.editLine, 10);
        Gfx.glyph(59169, sx + 8, sy, 18, sh, Th.sub, Th.fIconS, 1 + 4);
        int ex = sx + 32;
        int ew = sw - 32 - 10;
        int ey = sy + 6;
        int eh = 20;
        if (ex != sbx || ey != sby || ew != sbw) {
            sbx = ex;
            sby = ey;
            sbw = ew;
            search.setBounds(ex, ey, ew, eh);
        }
    }

    static String navTitle(int p) {
        if (p == 0) {
            return "进程";
        }
        if (p == 1) {
            return "性能";
        }
        if (p == 2) {
            return "应用历史记录";
        }
        if (p == 3) {
            return "启动应用";
        }
        if (p == 4) {
            return "用户";
        }
        if (p == 5) {
            return "详细信息";
        }
        if (p == 6) {
            return "服务";
        }
        return "设置";
    }

    static int navGlyph(int p) {
        if (p == 0) {
            return 59861;
        }
        if (p == 1) {
            return 59865;
        }
        if (p == 2) {
            return 59420;
        }
        if (p == 3) {
            return 59717;
        }
        if (p == 4) {
            return 59158;
        }
        if (p == 5) {
            return 59645;
        }
        if (p == 6) {
            return 59893;
        }
        return 59155;
    }

    static void navItem(int p, int y) {
        int x = 6;
        int w = navW - 12;
        int h = 40;
        int on = 0;
        if (p == page) {
            on = 1;
        }
        if (on != 0) {
            Gfx.round(x, y, w, h, 6, Th.navSel);
            Gfx.round(x, y + 12, 3, 16, 2, Th.accent);
        } else if (TmUi.hot(x, y, w, h) != 0) {
            Gfx.round(x, y, w, h, 6, Th.navHover);
        }
        Gfx.glyph(navGlyph(p), x + 14, y, 18, h, Th.text, Th.fIcon, 1 + 4);
        if (navCollapsed() == 0) {
            Gfx.text(navTitle(p), x + 46, y, w - 50, h, Th.text, Th.fBody, 4 + 8);
        }
        TmHit.add(x, y, w, h, 10, p);
    }

    static void paintNav() {
        int y = Th.STRIP_H;
        /* hamburger */
        if (TmUi.hot(6, y, 44, 40) != 0) {
            Gfx.round(6, y, 44, 40, 6, Th.navHover);
        }
        Gfx.glyph(59136, 6, y, 44, 40, Th.text, Th.fIcon, 1 + 4);
        TmHit.add(6, y, 44, 40, 11, 0);
        y = y + 48;
        int p = 0;
        while (p < 7) {
            navItem(p, y);
            y = y + 42;
            p = p + 1;
        }
        navItem(7, H - 48);
    }

    /* ---------------------------------------------------------------- command bar */
    static int bw(int glyphCp, String label) {
        int w = 20;
        if (glyphCp > 0) {
            w = w + 16;
        }
        if (label.length() > 0) {
            w = w + Gfx.textWidth(Th.fBody, label);
            if (glyphCp > 0) {
                w = w + 8;
            }
        }
        return w;
    }

    static void paintCmdBar(int px, int py, int pw) {
        Gfx.text(navTitle(page), px + 20, py, 300, Th.CMD_H, Th.text, Th.fTitle, 4 + 8);
        if (page == 2) {
            Gfx.text("当前运行进程自启动以来的累计资源使用量(系统不提供 30 天历史)", px + 220, py, pw - 240, Th.CMD_H, Th.sub, Th.fSmall, 2 + 4 + 8);
        }
        int n = 0;
        int[] gl = new int[8];
        ArrayList<String> lb = new ArrayList<String>();
        int[] en = new int[8];
        int[] id = new int[8];
        TmGrid g = activeGrid();
        TmRow sel = null;
        if (g != null) {
            sel = g.selected();
        }
        int hasSel = 0;
        if (sel != null && sel.kind == 0) {
            hasSel = 1;
        }
        if (page == 0) {
            gl[n] = 59559;
            lb.add("运行新任务");
            en[n] = 1;
            id[n] = 20;
            n = n + 1;
            gl[n] = 59187;
            lb.add("结束任务");
            en[n] = hasSel;
            id[n] = 21;
            n = n + 1;
            if (pw >= 760) {
                gl[n] = 1;
                lb.add("效率模式");
                en[n] = hasSel;
                id[n] = 22;
                n = n + 1;
            }
            gl[n] = 59154;
            lb.add("");
            en[n] = 1;
            id[n] = 23;
            n = n + 1;
        } else if (page == 5) {
            gl[n] = 59559;
            lb.add("运行新任务");
            en[n] = 1;
            id[n] = 20;
            n = n + 1;
            gl[n] = 59187;
            lb.add("结束任务");
            en[n] = hasSel;
            id[n] = 21;
            n = n + 1;
            gl[n] = 59154;
            lb.add("");
            en[n] = 1;
            id[n] = 23;
            n = n + 1;
        } else if (page == 3) {
            gl[n] = 59198;
            lb.add("启用");
            en[n] = hasSel;
            id[n] = 24;
            n = n + 1;
            gl[n] = 59187;
            lb.add("禁用");
            en[n] = hasSel;
            id[n] = 25;
            n = n + 1;
            gl[n] = 59154;
            lb.add("");
            en[n] = 1;
            id[n] = 23;
            n = n + 1;
        } else if (page == 4) {
            gl[n] = 59158;
            lb.add("断开连接");
            en[n] = hasSel;
            id[n] = 26;
            n = n + 1;
            gl[n] = 59136;
            lb.add("注销");
            en[n] = hasSel;
            id[n] = 27;
            n = n + 1;
        } else if (page == 6) {
            gl[n] = 59240;
            lb.add("启动");
            en[n] = hasSel;
            id[n] = 28;
            n = n + 1;
            gl[n] = 59187;
            lb.add("停止");
            en[n] = hasSel;
            id[n] = 29;
            n = n + 1;
            gl[n] = 59161;
            lb.add("重新启动");
            en[n] = hasSel;
            id[n] = 30;
            n = n + 1;
            gl[n] = 59893;
            lb.add("打开服务");
            en[n] = 1;
            id[n] = 31;
            n = n + 1;
        } else if (page == 1) {
            gl[n] = 59155;
            lb.add("资源监视器");
            en[n] = 1;
            id[n] = 32;
            n = n + 1;
        }
        int total = 0;
        int i = 0;
        while (i < n) {
            total = total + bw(gl[i], lb.get(i)) + 2;
            i = i + 1;
        }
        int x = px + pw - total - 12;
        i = 0;
        while (i < n) {
            int w = bw(gl[i], lb.get(i));
            if (gl[i] == 1) {
                /* efficiency leaf as the icon */
                int col = Th.text;
                if (en[i] == 0) {
                    col = Th.disabled;
                } else if (TmUi.hot(x, py + 8, w, 33) != 0) {
                    Gfx.round(x, py + 8, w, 33, 4, Th.hover);
                }
                TmUi.leaf(x + 10, py + 17, col);
                Gfx.text(lb.get(i), x + 34, py + 8, w - 34, 33, col, Th.fBody, 4 + 8);
                if (en[i] != 0) {
                    TmHit.add(x, py + 8, w, 33, id[i], 0);
                }
            } else {
                TmUi.cmdButton(x, py + 8, 33, gl[i], lb.get(i), en[i], id[i], 0);
            }
            x = x + w + 2;
            if (page == 0 && i == 0) {
                Gfx.fill(x, py + 14, 1, 21, Th.line);
                x = x + 3;
            }
            i = i + 1;
        }
    }

    /* ------------------------------------------------------------------ input */
    static int inGrid(TmGrid g, int x, int y) {
        if (g == null) {
            return 0;
        }
        if (x >= g.hx && x < g.hx + g.hw && y >= g.hy && y < g.by + g.bh) {
            return 1;
        }
        return 0;
    }

    static void onMouse(MouseEvent ev) {
        int t = ev.eventType;
        int x = ev.x;
        int y = ev.y;
        TmUi.mx = x;
        TmUi.my = y;
        TmGrid g = activeGrid();
        if (t == 5) {
            if (g != null) {
                g.onMove(x, y, ev.button);
            }
            cv.invalidate();
            return;
        }
        if (t == 7) {
            TmUi.mx = -100;
            TmUi.my = -100;
            if (g != null) {
                g.onLeave();
            }
            cv.invalidate();
            return;
        }
        if (t == 8) {
            if (g != null) {
                g.onWheel(ev.button);
            }
            cv.invalidate();
            return;
        }
        if (t == 2) {
            if (g != null) {
                g.onUp();
            }
            cv.invalidate();
            return;
        }
        if (t != 1 && t != 4) {
            return;
        }
        lastBtn = ev.button;
        if (inGrid(g, x, y) != 0) {
            if (t == 4) {
                int ri = g.rowAt(x, y);
                if (ri >= 0 && ev.button == 1) {
                    TmRow r = g.rows.get(ri);
                    if (r.expandable != 0) {
                        g.selKey = r.key;
                        g.setExpanded(r.key, 1 - r.expanded);
                        rebuild();
                    } else if (page == 0 && r.kind == 0) {
                        /* double click on a process opens its details */
                        doAction(5);
                    }
                    cv.invalidate();
                }
                return;
            }
            int res = g.onDown(x, y, ev.button);
            if (res == 3 || res == 4) {
                rebuild();
            } else if (res == 2) {
                showRowMenu(g);
            } else if (res == 5) {
                showColumnMenu(g, x);
            }
            cv.invalidate();
            return;
        }
        if (t != 1 || ev.button != 1) {
            return;
        }
        int hi = TmHit.at(x, y);
        if (hi < 0) {
            return;
        }
        int id = TmHit.hid[hi];
        int data = TmHit.hdata[hi];
        if (id == 10) {
            gotoPage(data);
        } else if (id == 11) {
            navUser = 1 - navUser;
        } else if (id == 200) {
            TmPerf.click(data);
        } else if (id == 210) {
            TmPerf.coreView = 1 - TmPerf.coreView;
        } else if (id == 300) {
            setSpeed(data);
        } else if (id == 301) {
            Th.mode = data;
            applyTheme();
        } else if (id == 302) {
            if (data == 0) {
                TmPerf.coreView = 1 - TmPerf.coreView;
            } else {
                TmPerf.showKernel = 1 - TmPerf.showKernel;
            }
        } else if (id == 303) {
            if (TmNative.relaunchElevated() == 0) {
                win.close();
            }
        } else if (id == 23) {
            showMoreMenu();
        } else if (id >= 20 && id < 40) {
            doAction(id);
        }
        cv.invalidate();
    }

    static void onKey(KeyEvent ev) {
        if (ev == null) {
            return;
        }
        if (ev.rawMods >= 16) {
            return;
        }
        int vk = ev.keyCode;
        int ctrl = ev.ctrlDown;
        if (vk == 116) {
            refresh();
            return;
        }
        if (ctrl != 0 && (vk == 70 || vk == 102)) {
            search.requestFocus();
            return;
        }
        if (vk == 27) {
            if (filterRaw.length() > 0) {
                search.setText("");
                onSearch();
            }
            return;
        }
        TmGrid g = activeGrid();
        if (g == null) {
            return;
        }
        if (vk == 46 && (page == 0 || page == 5)) {
            doAction(21);
            return;
        }
        int r = g.onKey(vk, ev.rawMods);
        if (r == 4) {
            rebuild();
        }
        if (r != 0) {
            cv.invalidate();
        }
    }

    /* ---------------------------------------------------------------- context */
    static void mi(Menu m, String label, int act, int enabled) {
        MenuItem it = new MenuItem(label, () -> {
            TmApp.doAction(act);
        });
        if (enabled == 0) {
            it.setEnabled(false);
        }
        m.add(it);
    }

    static void showMoreMenu() {
        ContextMenu m = new ContextMenu();
        if (page == 0 || page == 4) {
            mi(m, "全部展开", 40, 1);
            mi(m, "全部折叠", 41, 1);
            m.addSeparator();
        }
        mi(m, "立即刷新 (F5)", 42, 1);
        if (speed == 3) {
            mi(m, "继续自动更新", 43, 1);
        } else {
            mi(m, "暂停自动更新", 44, 1);
        }
        if (page == 3) {
            m.addSeparator();
            mi(m, "重新读取启动项", 45, 1);
        }
        m.addSeparator();
        mi(m, "打开资源监视器", 32, 1);
        m.showAt(0, 0);
    }

    static void showColumnMenu(TmGrid g, int x) {
        ContextMenu m = new ContextMenu();
        int i = 0;
        while (i < g.cols.size()) {
            TmCol c = g.cols.get(i);
            if (c.id != Col.NAME && !(g == gSvc && c.id == 0) && !(g == gStart && c.id == 0)) {
                int code = 100 + g.gid * 20 + i;
                MenuItem it = new MenuItem(c.title, () -> {
                    TmApp.doAction(code);
                });
                it.setChecked(c.visible != 0);
                m.add(it);
            }
            i = i + 1;
        }
        m.showAt(0, 0);
    }

    static void showRowMenu(TmGrid g) {
        TmRow r = g.selected();
        if (r == null || r.kind != 0) {
            return;
        }
        ContextMenu m = new ContextMenu();
        if (page == 0 || page == 4) {
            if (r.ctxKind == 5) {
                mi(m, "展开/折叠", 1, 1);
                m.addSeparator();
                mi(m, "断开连接", 26, 1);
                mi(m, "注销", 27, 1);
            } else if (r.ctxKind == 3) {
                mi(m, "启动", 28, 1);
                mi(m, "停止", 29, 1);
                mi(m, "重新启动", 30, 1);
                m.addSeparator();
                mi(m, "转到详细信息", 5, 1);
                mi(m, "在线搜索", 7, 1);
            } else {
                if (r.expandable != 0) {
                    if (r.expanded != 0) {
                        mi(m, "折叠", 1, 1);
                    } else {
                        mi(m, "展开", 1, 1);
                    }
                    m.addSeparator();
                }
                mi(m, "结束任务", 21, 1);
                mi(m, "效率模式", 22, 1);
                mi(m, "创建内存转储文件", 4, 1);
                m.addSeparator();
                mi(m, "转到详细信息", 5, 1);
                if (r.members.length() > 0 && TmStore.svcCountForPid(r.pid) > 0) {
                    mi(m, "转到服务", 18, 1);
                }
                mi(m, "打开文件所在的位置", 6, 1);
                mi(m, "在线搜索", 7, 1);
                mi(m, "属性", 8, 1);
                m.addSeparator();
                mi(m, "复制 PID", 9, 1);
            }
        } else if (page == 5) {
            mi(m, "结束任务", 21, 1);
            mi(m, "结束进程树", 10, 1);
            Menu pm = new Menu("设置优先级");
            mi(pm, "实时", 11, 1);
            mi(pm, "高", 12, 1);
            mi(pm, "高于正常", 13, 1);
            mi(pm, "正常", 14, 1);
            mi(pm, "低于正常", 15, 1);
            mi(pm, "低", 16, 1);
            m.addMenu(pm);
            mi(m, "挂起/继续", 17, 1);
            mi(m, "效率模式", 22, 1);
            m.addSeparator();
            mi(m, "分析等待链(不可用)", 99, 0);
            mi(m, "创建转储文件", 4, 1);
            if (TmStore.svcCountForPid(r.pid) > 0) {
                mi(m, "转到服务", 18, 1);
            }
            m.addSeparator();
            mi(m, "打开文件所在的位置", 6, 1);
            mi(m, "在线搜索", 7, 1);
            mi(m, "属性", 8, 1);
            mi(m, "复制 PID", 9, 1);
        } else if (page == 6) {
            mi(m, "启动", 28, 1);
            mi(m, "停止", 29, 1);
            mi(m, "重新启动", 30, 1);
            m.addSeparator();
            mi(m, "打开服务", 31, 1);
            if (r.pid > 0) {
                mi(m, "转到详细信息", 5, 1);
            }
            mi(m, "在线搜索", 7, 1);
        } else if (page == 3) {
            mi(m, "启用", 24, 1);
            mi(m, "禁用", 25, 1);
            m.addSeparator();
            mi(m, "打开文件所在的位置", 6, 1);
            mi(m, "在线搜索", 7, 1);
            mi(m, "属性", 8, 1);
        } else {
            return;
        }
        m.showAt(0, 0);
    }

    /* ---------------------------------------------------------------- actions */
    static ArrayList<TmProc> procsOf(TmRow r) {
        ArrayList<TmProc> out = new ArrayList<TmProc>();
        if (r == null) {
            return out;
        }
        if (r.members.length() == 0) {
            TmProc p = TmStore.byPidOf(r.pid);
            if (p != null) {
                out.add(p);
            }
            return out;
        }
        int start = 0;
        String s = r.members;
        while (start <= s.length()) {
            int c = s.indexOfFrom(",", start);
            int end = c;
            if (end < 0) {
                end = s.length();
            }
            if (end > start) {
                int pid = AmParse.intOr(s.substring(start, end), -1);
                TmProc p = TmStore.byPidOf(pid);
                if (p != null) {
                    out.add(p);
                }
            }
            if (c < 0) {
                break;
            }
            start = c + 1;
        }
        return out;
    }

    static int critical(TmProc p) {
        if (p.pid <= 4) {
            return 1;
        }
        String n = p.name.toLowerCase();
        if (n.equals("smss.exe") || n.equals("csrss.exe") || n.equals("wininit.exe") || n.equals("services.exe")
                || n.equals("lsass.exe") || n.equals("winlogon.exe") || n.equals("registry")
                || n.equals("memory compression")) {
            return 1;
        }
        return 0;
    }

    static void endTask(TmRow r) {
        ArrayList<TmProc> ps = procsOf(r);
        if (ps.size() == 0) {
            return;
        }
        int failed = 0;
        String err = "";
        int refused = 0;
        int i = 0;
        while (i < ps.size()) {
            TmProc p = ps.get(i);
            if (critical(p) != 0) {
                refused = refused + 1;
            } else if (AmNative.checkIdentity(p.pid, p.startSec, p.startUsec) == 1) {
                int e = AmNative.signal(p.pid, 9);
                if (e != 0) {
                    failed = failed + 1;
                    err = AmKill.describeError(e);
                }
            }
            i = i + 1;
        }
        if (refused > 0) {
            Dialogs.error(win, "无法结束 Windows 关键系统进程。");
        } else if (failed > 0) {
            Dialogs.error(win, "无法结束进程(" + String.valueOf(failed) + " 个失败): " + err);
        }
        refresh();
    }

    static void doEfficiency(TmRow r) {
        ArrayList<TmProc> ps = procsOf(r);
        if (ps.size() == 0) {
            return;
        }
        int allOn = 1;
        int i = 0;
        while (i < ps.size()) {
            if (ps.get(i).eco != 1) {
                allOn = 0;
            }
            i = i + 1;
        }
        int enable = 1;
        if (allOn != 0) {
            enable = 0;
        } else if (!Dialogs.confirm(win, "启用效率模式会降低该进程的优先级和资源使用，可能导致应用响应变慢。是否继续？")) {
            return;
        }
        int fail = 0;
        i = 0;
        while (i < ps.size()) {
            int e = AmNative.setEfficiency(ps.get(i).pid, enable);
            if (e != 0) {
                fail = fail + 1;
            }
            i = i + 1;
        }
        if (fail > 0) {
            Dialogs.error(win, "部分进程无法更改效率模式(权限不足或系统不支持)。");
        }
        refresh();
    }

    static void gotoDetails(TmProc p) {
        page = 5;
        TmStore.sampleProcs(1);
        rebuild();
        gDet.selKey = "d:" + p.key;
        int ix = gDet.indexOfKey(gDet.selKey);
        gDet.ensureVisible(ix);
        cv.invalidate();
    }

    static TmSvc selectedSvc(TmRow r) {
        if (r == null) {
            return null;
        }
        return TmStore.svcByName(r.ref);
    }

    static void svcOp(TmRow r, int op) {
        TmSvc s = selectedSvc(r);
        if (s == null) {
            return;
        }
        int e = TmNative.serviceControl(s.name, op);
        if (e != 0) {
            Dialogs.error(win, "无法操作服务“" + s.display + "”(错误 " + String.valueOf(e) + (e == 5 ? "：拒绝访问，请以管理员身份运行" : "") + ")。");
        }
        TmStore.sampleServices();
        rebuild();
    }

    static void doAction(int act) {
        TmGrid g = activeGrid();
        TmRow r = null;
        if (g != null) {
            r = g.selected();
        }
        if (act >= 100) {
            /* column visibility */
            if (g == null) {
                return;
            }
            int ci = (act - 100) - g.gid * 20;
            if (ci >= 0 && ci < g.cols.size()) {
                TmCol c = g.cols.get(ci);
                c.visible = 1 - c.visible;
                rebuild();
                cv.invalidate();
            }
            return;
        }
        if (act == 20) {
            TmDialogs.showRun(win);
            return;
        }
        if (act == 32) {
            TmNative.openResourceMonitor();
            return;
        }
        if (act == 40 || act == 41) {
            if (g != null) {
                int i = 0;
                while (i < g.rows.size()) {
                    TmRow x = g.rows.get(i);
                    if (x.expandable != 0) {
                        g.setExpanded(x.key, act == 40 ? 1 : 0);
                    }
                    i = i + 1;
                }
                if (page == 0) {
                    /* children of collapsed groups are not rows yet: expand by group key */
                    int gi = 0;
                    while (gi < TmBuild.groups.size()) {
                        TmGroup gr = TmBuild.groups.get(gi);
                        if (gr.members.size() > 1 || gr.svcHost > 0) {
                            g.setExpanded(gr.key, act == 40 ? 1 : 0);
                        }
                        gi = gi + 1;
                    }
                }
                rebuild();
            }
            cv.invalidate();
            return;
        }
        if (act == 42) {
            refresh();
            return;
        }
        if (act == 43) {
            setSpeed(1);
            return;
        }
        if (act == 44) {
            setSpeed(3);
            return;
        }
        if (act == 45) {
            TmStore.loadStartup();
            rebuild();
            cv.invalidate();
            return;
        }
        if (r == null || r.kind != 0) {
            return;
        }
        if (act == 1) {
            if (r.expandable != 0) {
                g.setExpanded(r.key, 1 - r.expanded);
                rebuild();
            }
        } else if (act == 21) {
            if (page == 0 || page == 5) {
                endTask(r);
            }
        } else if (act == 22) {
            doEfficiency(r);
        } else if (act == 4) {
            ArrayList<TmProc> ps = procsOf(r);
            if (ps.size() > 0) {
                String path = "C:\\Windows\\Temp\\" + Fmt.stripExe(ps.get(0).name) + "-" + String.valueOf(ps.get(0).pid) + ".dmp";
                int e = TmNative.createDump(ps.get(0).pid, path);
                if (e == 0) {
                    Dialogs.info(win, "已创建转储文件:\n" + path);
                } else {
                    Dialogs.error(win, "无法创建转储文件(错误 " + String.valueOf(e) + ")。");
                }
            }
        } else if (act == 5) {
            ArrayList<TmProc> ps = procsOf(r);
            if (ps.size() > 0) {
                TmProc target = ps.get(0);
                if (r.root != null) {
                    target = r.root;
                }
                gotoDetails(target);
            }
        } else if (act == 6) {
            if (page == 3) {
                String p = "";
                int i = 0;
                while (i < TmStore.startups.size()) {
                    if (TmStore.startups.get(i).id.equals(r.ref)) {
                        p = TmStore.startups.get(i).path;
                    }
                    i = i + 1;
                }
                if (p.length() > 0) {
                    TmNative.openPath("/select," + p);
                }
            } else {
                ArrayList<TmProc> ps = procsOf(r);
                if (ps.size() > 0) {
                    AmNative.openFileLocation(ps.get(0).pid);
                }
            }
        } else if (act == 7) {
            String q = r.cells.size() > 0 ? r.cells.get(0) : "";
            TmNative.searchOnline(q);
        } else if (act == 8) {
            ArrayList<TmProc> ps = procsOf(r);
            if (ps.size() > 0) {
                TmNative.properties(ps.get(0).pid);
            }
        } else if (act == 9) {
            TmNative.copyText(String.valueOf(r.pid));
        } else if (act == 10) {
            if (Dialogs.confirm(win, "确定要结束“" + r.cells.get(0) + "”及其所有子进程吗？")) {
                TmNative.killTree(r.pid);
                refresh();
            }
        } else if (act >= 11 && act <= 16) {
            int e = TmNative.setPriority(r.pid, act - 11);
            if (e != 0) {
                Dialogs.error(win, "无法更改优先级(错误 " + String.valueOf(e) + ")。");
            }
            refresh();
        } else if (act == 17) {
            ArrayList<TmProc> ps = procsOf(r);
            if (ps.size() > 0) {
                int on = 1;
                if (ps.get(0).suspended != 0) {
                    on = 0;
                }
                int e = TmNative.suspend(ps.get(0).pid, on);
                if (e != 0) {
                    Dialogs.error(win, "操作失败(错误 " + String.valueOf(e) + ")。");
                }
                refresh();
            }
        } else if (act == 18) {
            TmSvc first = TmBuild.firstSvc(r.pid);
            page = 6;
            TmStore.sampleServices();
            rebuild();
            if (first != null) {
                gSvc.selKey = "v:" + first.name;
                gSvc.ensureVisible(gSvc.indexOfKey(gSvc.selKey));
            }
            cv.invalidate();
        } else if (act == 28) {
            svcOp(r, 1);
        } else if (act == 29) {
            svcOp(r, 2);
        } else if (act == 30) {
            svcOp(r, 3);
        } else if (act == 31) {
            TmNative.openServicesMsc();
        } else if (act == 24 || act == 25) {
            int e = TmNative.startupSet(r.ref, act == 24 ? 1 : 0);
            if (e != 0) {
                Dialogs.error(win, "无法更改启动项状态(错误 " + String.valueOf(e) + (e == 5 ? "：需要管理员权限" : "") + ")。");
            }
            TmStore.loadStartup();
            rebuild();
        } else if (act == 26 || act == 27) {
            if (r.ownerSession >= 0) {
                int e = TmNative.sessionAction(r.ownerSession, act == 26 ? 1 : 2);
                if (e != 0) {
                    Dialogs.error(win, "操作失败(错误 " + String.valueOf(e) + ")。");
                }
                TmStore.sampleSessions();
                rebuild();
            }
        }
        cv.invalidate();
    }
}
