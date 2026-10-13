/**
 * JNative Task Monitor — Windows desktop process monitor.
 * Sampling via jn_am (Toolhelp32 / GetProcessTimes / GetProcessMemoryInfo).
 * UI via JNative UiLib + Win32 backend.
 *
 * @author 张晓达
 */
public class TaskMonitor {
    public static void main(String[] args) {
        if (args != null && args.length > 0) {
            String a0 = args[0];
            if (a0.equals("--dump") || a0.equals("--bench") || a0.equals("--self")
                    || a0.equals("--signal-self") || a0.equals("--help")
                    || a0.equals("--find") || a0.equals("--signal") || a0.equals("--check")) {
                AmCore.runCli(args);
                return;
            }
        }
        Application.setAppName("JNative Task Monitor");
        Application.setQuitOnLastWindowClose(true);
        Application app = new Application();
        Theme.setAppearanceMode(Theme.MODE_SYSTEM);
        TmWindow.show();
        app.run();
    }
}

class TmUi {
    static Window window;
    static TableView<AmProcRow> table;
    static SearchField search;
    static Label footPrimary;
    static Label footStatus;
    static Button detailBtn;
    static Button termBtn;
    static BindingScope scope;
    static AmViewState state;
    static ArrayList<AmProcRow> viewRows;
    static ArrayList<int> cpuHistory;
    static String searchText;
    static int sortCol;
    static int sortAsc;
    static int selPid;
    static long selStartSec;
    static int selStartUsec;
    static int alive;
    static int paused;
    static int refreshMs;
    static int refreshGen;
    static int sampleToken;
    static int tableDirty;
    static long lastTableRebuildNs;
    static Window detailWin;
    static Label detailBody;
    static String detailCopyText;
    static int detailPid;
    static long detailStartSec;
    static int detailStartUsec;

    static void ensure() {
        if (state == null) {
            state = new AmViewState();
        }
        if (viewRows == null) {
            viewRows = new ArrayList<AmProcRow>();
        }
        if (cpuHistory == null) {
            cpuHistory = new ArrayList<int>();
        }
        if (searchText == null) {
            searchText = "";
        }
        if (scope == null) {
            scope = new BindingScope();
        }
        if (sortCol == 0 && sortAsc == 0) {
            sortCol = 3;
            sortAsc = 0;
        }
        if (refreshMs == 0) {
            refreshMs = 1000;
        }
        alive = 1;
        selPid = -1;
        detailPid = -1;
    }
}

class TmWindow {
    static void show() {
        TmUi.ensure();
        TmUi.window = new Window("JNative Task Monitor", 1300, 850);
        TmUi.window.setMinSize(900, 520);
        TmUi.window.onClose(() -> {
            TmUi.alive = 0;
            TmUi.sampleToken = TmUi.sampleToken + 1;
            TmUi.refreshGen = TmUi.refreshGen + 1;
            if (TmUi.scope != null) {
                TmUi.scope.dispose();
                TmUi.scope = null;
            }
        });

        VBox root = new VBox();
        root.spacing = 0;
        root.padding = 0;

        HBox toolbar = new HBox();
        toolbar.spacing = 8;
        toolbar.padding = 8;
        toolbar.prefHeight = 44;
        toolbar.height = 44;
        TmUi.detailBtn = new Button("详情");
        TmUi.detailBtn.onClick(() -> { TmActions.showDetail(); });
        TmUi.termBtn = new Button("结束进程");
        TmUi.termBtn.onClick(() -> { TmActions.confirmTerminate(); });
        TmUi.search = new SearchField();
        TmUi.search.setPlaceholder("搜索名称 / PID");
        TmUi.search.setOnQueryChanged(() -> { TmActions.onSearchChanged(); });
        TmUi.search.prefWidth = 260;
        TmUi.search.width = 260;
        Label flex = new Label("");
        flex.hgrow = 1;
        toolbar.add(TmUi.detailBtn);
        toolbar.add(TmUi.termBtn);
        toolbar.add(flex);
        toolbar.add(TmUi.search);

        TmUi.table = new TableView<AmProcRow>();
        TmUi.table.vgrow = 1;
        TmUi.table.prefHeight = 640;
        TmUi.table.height = 640;
        TmUi.table.setRowIdProvider(new TmRowIds());
        TmUi.table.setEmptyMessage("正在采样…");
        TmActions.rebuildColumns();
        TmUi.table.onSelectionChanged((ev) -> { TmActions.onSelect(ev.selectedIndex); });
        TmUi.table.onDoubleClick((ev) -> { TmActions.showDetail(); });
        TmUi.table.onHeaderClick((ev) -> { TmActions.sort(ev.selectedIndex); });

        HBox footer = new HBox();
        footer.spacing = 12;
        footer.padding = 10;
        footer.prefHeight = 48;
        footer.height = 48;
        TmUi.footPrimary = new Label("CPU\n等待采样");
        TmUi.footPrimary.setPrefSize(420, 36);
        Theme.applyStyle(TmUi.footPrimary, Theme.STYLE_BODY);
        TmUi.footStatus = new Label("就绪");
        TmUi.footStatus.setPrefSize(240, 36);
        Theme.applyStyle(TmUi.footStatus, Theme.STYLE_SECONDARY);
        Label fflex = new Label("");
        fflex.hgrow = 1;
        footer.add(TmUi.footPrimary);
        footer.add(fflex);
        footer.add(TmUi.footStatus);

        root.add(toolbar);
        root.add(TmUi.table);
        root.add(footer);
        TmUi.window.setContent(root);
        TmUi.window.show();
        TmActions.warmupAndArm();
    }
}

class TmRowIds implements TableRowIdProvider<AmProcRow> {
    public String rowId(AmProcRow row) {
        if (row == null || row.sample == null) {
            return "";
        }
        return row.sample.identityKey();
    }
}

class TmColName implements ColumnRenderer<AmProcRow> {
    String getTitle() {
        return TmActions.colTitle(0, "进程名称");
    }
    String renderRow(AmProcRow row) {
        if (row == null || row.sample == null || row.sample.name == null) {
            return "N/A";
        }
        return row.sample.name;
    }
}

class TmColPid implements ColumnRenderer<AmProcRow> {
    String getTitle() {
        return TmActions.colTitle(1, "PID");
    }
    String renderRow(AmProcRow row) {
        if (row == null || row.sample == null) {
            return "N/A";
        }
        return String.valueOf(row.sample.pid);
    }
}

class TmColUser implements ColumnRenderer<AmProcRow> {
    String getTitle() {
        return TmActions.colTitle(2, "用户");
    }
    String renderRow(AmProcRow row) {
        if (row == null || row.sample == null || row.sample.user == null) {
            return "N/A";
        }
        return row.sample.user;
    }
}

class TmColCpu implements ColumnRenderer<AmProcRow> {
    String getTitle() {
        return TmActions.colTitle(3, "CPU %");
    }
    String renderRow(AmProcRow row) {
        if (row == null) {
            return "—";
        }
        return AmFormat.cpuPct(row.cpuMilli);
    }
}

class TmColMem implements ColumnRenderer<AmProcRow> {
    String getTitle() {
        return TmActions.colTitle(4, "内存占用");
    }
    String renderRow(AmProcRow row) {
        if (row == null || row.sample == null) {
            return "N/A";
        }
        if ((row.sample.flags & AmFlags.FOOTPRINT) != 0) {
            return AmFormat.bytes(row.sample.footprint);
        }
        if ((row.sample.flags & AmFlags.RSS) != 0) {
            return AmFormat.bytes(row.sample.rss);
        }
        return "N/A";
    }
}

class TmColThr implements ColumnRenderer<AmProcRow> {
    String getTitle() {
        return TmActions.colTitle(5, "线程");
    }
    String renderRow(AmProcRow row) {
        if (row == null || row.sample == null) {
            return "N/A";
        }
        return AmFormat.threads(row.sample);
    }
}

class TmActions {
    static String colTitle(int col, String base) {
        if (TmUi.sortCol != col) {
            return base;
        }
        if (TmUi.sortAsc != 0) {
            return base + " ▲";
        }
        return base + " ▼";
    }

    static void rebuildColumns() {
        if (TmUi.table == null) {
            return;
        }
        TmUi.table.columns.clear();
        TmUi.table.addColumnDef(colTitle(0, "进程名称"), new TmColName(), 0, "L", 1);
        TmUi.table.addColumnDef(colTitle(1, "PID"), new TmColPid(), 88, "R", 0);
        TmUi.table.addColumnDef(colTitle(2, "用户"), new TmColUser(), 120, "L", 0);
        TmUi.table.addColumnDef(colTitle(3, "CPU %"), new TmColCpu(), 88, "R", 0);
        TmUi.table.addColumnDef(colTitle(4, "内存占用"), new TmColMem(), 110, "R", 0);
        TmUi.table.addColumnDef(colTitle(5, "线程"), new TmColThr(), 72, "R", 0);
    }

    static void onSearchChanged() {
        if (TmUi.search == null) {
            return;
        }
        String q = TmUi.search.getQuery();
        if (q == null) {
            q = "";
        }
        TmUi.searchText = q;
        TmUi.tableDirty = 1;
        maybeRebuildTable(1);
    }

    static void onSelect(int index) {
        if (TmUi.viewRows == null || index < 0 || index >= TmUi.viewRows.size()) {
            TmUi.selPid = -1;
            return;
        }
        AmProcRow r = TmUi.viewRows.get(index);
        if (r == null || r.sample == null) {
            TmUi.selPid = -1;
            return;
        }
        TmUi.selPid = r.sample.pid;
        TmUi.selStartSec = r.sample.startSec;
        TmUi.selStartUsec = r.sample.startUsec;
        refreshDetailIfOpen();
    }

    static void sort(int col) {
        if (col < 0 || col > 5) {
            return;
        }
        if (TmUi.sortCol == col) {
            TmUi.sortAsc = TmUi.sortAsc != 0 ? 0 : 1;
        } else {
            TmUi.sortCol = col;
            TmUi.sortAsc = 0;
        }
        rebuildColumns();
        TmUi.tableDirty = 1;
        maybeRebuildTable(1);
    }

    static AmProcRow selectedRow() {
        if (TmUi.selPid <= 0 || TmUi.state == null) {
            return null;
        }
        return TmUi.state.findByIdentity(TmUi.selPid, TmUi.selStartSec, TmUi.selStartUsec);
    }

    static void warmupAndArm() {
        Thread t = new Thread(() -> {
            AmSnapshot a = AmSampler.take();
            AmSnapshot b = AmSampler.take();
            Application.invokeLater(() -> {
                if (TmUi.alive == 0) {
                    return;
                }
                if (a != null) {
                    applyOneSnapshot(a);
                }
                if (b != null) {
                    applyOneSnapshot(b);
                }
                finishSampleUi(1);
                armRefresh();
            });
        });
        t.start();
    }

    static void applyOneSnapshot(AmSnapshot snap) {
        if (snap == null) {
            return;
        }
        TmUi.state.applySnapshot(snap);
        if (TmUi.state.sysCpuMilli >= 0) {
            TmUi.cpuHistory.add(TmUi.state.sysCpuMilli);
            while (TmUi.cpuHistory.size() > 60) {
                TmUi.cpuHistory.removeAt(0);
            }
        }
    }

    static void finishSampleUi(int force) {
        updateFooter();
        if (TmUi.state.sampleCount >= 2) {
            if (force != 0 || TmUi.tableDirty != 0 || TmUi.lastTableRebuildNs == 0) {
                maybeRebuildTable(1);
            }
        }
        TmUi.footStatus.setText(TmUi.paused != 0 ? "已暂停" : "刷新 " + String.valueOf(TmUi.refreshMs) + " ms");
        refreshDetailIfOpen();
    }

    static void requestSample(int force) {
        if (TmUi.alive == 0) {
            return;
        }
        int token = TmUi.sampleToken;
        Thread t = new Thread(() -> {
            AmSnapshot snap = AmSampler.take();
            Application.invokeLater(() -> {
                if (TmUi.alive == 0 || token != TmUi.sampleToken) {
                    return;
                }
                if (snap != null) {
                    applyOneSnapshot(snap);
                }
                finishSampleUi(force);
            });
        });
        t.start();
    }

    static void armRefresh() {
        int gen = TmUi.refreshGen;
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(TmUi.refreshMs);
            } catch (Exception e) {
            }
            Application.invokeLater(() -> {
                if (TmUi.alive == 0 || gen != TmUi.refreshGen) {
                    return;
                }
                if (TmUi.paused == 0) {
                    requestSample(0);
                }
                armRefresh();
            });
        });
        t.start();
    }

    static void updateFooter() {
        if (TmUi.footPrimary == null || TmUi.state == null) {
            return;
        }
        String cpu = "—";
        if (TmUi.state.sysCpuMilli >= 0) {
            cpu = AmFormat.cpuPct(TmUi.state.sysCpuMilli);
        }
        int nproc = 0;
        int nthr = 0;
        if (TmUi.state.rows != null) {
            nproc = TmUi.state.rows.size();
            int i = 0;
            while (i < TmUi.state.rows.size()) {
                AmProcRow r = TmUi.state.rows.get(i);
                if (r != null && r.sample != null && (r.sample.flags & AmFlags.NTHREADS) != 0) {
                    nthr = nthr + r.sample.nthreads;
                }
                i = i + 1;
            }
        }
        String hist = "";
        if (TmUi.cpuHistory != null && TmUi.cpuHistory.size() > 0) {
            hist = "  ·  历史 " + String.valueOf(TmUi.cpuHistory.size()) + "s";
        }
        TmUi.footPrimary.setText("系统 CPU " + cpu + "\n进程 " + String.valueOf(nproc)
                + "  ·  线程 " + String.valueOf(nthr) + hist);
    }

    static void maybeRebuildTable(int force) {
        if (TmUi.table == null || TmUi.state == null) {
            return;
        }
        if (force == 0 && TmUi.tableDirty == 0) {
            return;
        }
        ArrayList<AmProcRow> filtered = TmUi.state.filtered(TmUi.searchText);
        ArrayList<AmProcRow> sorted = sortRows(filtered, TmUi.sortCol, TmUi.sortAsc);
        ArrayList<AmProcRow> shown = new ArrayList<AmProcRow>();
        int maxShow = 800;
        int si = 0;
        while (si < sorted.size() && si < maxShow) {
            AmProcRow rr = sorted.get(si);
            if (rr != null && rr.sample != null) {
                if (rr.sample.name == null) {
                    rr.sample.name = "";
                }
                if (rr.sample.user == null) {
                    rr.sample.user = "N/A";
                }
                shown.add(rr);
            }
            si = si + 1;
        }
        TmUi.viewRows = shown;
        TmUi.table.setItems(TmUi.viewRows);
        if (TmUi.selPid > 0) {
            String want = String.valueOf(TmUi.selPid) + ":"
                    + AmParse.longToString(TmUi.selStartSec) + ":"
                    + String.valueOf(TmUi.selStartUsec);
            TmUi.table.selectByRowId(want);
            if (TmUi.table.getSelectedRowId() == null
                    || !want.equals(TmUi.table.getSelectedRowId())) {
                TmUi.selPid = -1;
            }
        }
        TmUi.tableDirty = 0;
        TmUi.lastTableRebuildNs = AmNative.monoNs();
    }

    static ArrayList<AmProcRow> sortRows(ArrayList<AmProcRow> src, int col, int asc) {
        ArrayList<AmProcRow> out = new ArrayList<AmProcRow>();
        int i = 0;
        while (i < src.size()) {
            out.add(src.get(i));
            i = i + 1;
        }
        int a = 0;
        while (a < out.size()) {
            int b = a + 1;
            while (b < out.size()) {
                if (cmp(out.get(a), out.get(b), col, asc) > 0) {
                    AmProcRow tmp = out.get(a);
                    out.set(a, out.get(b));
                    out.set(b, tmp);
                }
                b = b + 1;
            }
            a = a + 1;
        }
        return out;
    }

    static int cmp(AmProcRow x, AmProcRow y, int col, int asc) {
        if (x == null || y == null || x.sample == null || y.sample == null) {
            return 0;
        }
        int c = 0;
        if (col == 0) {
            c = x.sample.name.compareTo(y.sample.name);
        } else if (col == 1) {
            c = x.sample.pid - y.sample.pid;
        } else if (col == 2) {
            c = x.sample.user.compareTo(y.sample.user);
        } else if (col == 3) {
            c = x.cpuMilli - y.cpuMilli;
        } else if (col == 4) {
            long mx = x.sample.footprint;
            long my = y.sample.footprint;
            if (mx < my) {
                c = -1;
            } else if (mx > my) {
                c = 1;
            }
        } else if (col == 5) {
            c = x.sample.nthreads - y.sample.nthreads;
        }
        if (asc == 0) {
            c = 0 - c;
        }
        return c;
    }

    static void showDetail() {
        AmProcRow r = selectedRow();
        if (r == null) {
            Dialogs.info(TmUi.window, "请先选择一个进程");
            return;
        }
        if (TmUi.detailWin == null) {
            TmUi.detailWin = new Window("进程详情", 460, 420);
            TmUi.detailWin.setOwnerWindow(TmUi.window);
            TmUi.detailWin.onClose(() -> {
                TmUi.detailWin = null;
                TmUi.detailBody = null;
                TmUi.detailPid = -1;
            });
            VBox box = new VBox();
            box.spacing = 8;
            box.padding = 16;
            TmUi.detailBody = new Label("");
            TmUi.detailBody.setPrefSize(420, 300);
            Button copy = new Button("复制");
            copy.onClick(() -> {
                String t = TmUi.detailCopyText;
                if (t == null) {
                    t = "";
                }
                Clipboard.setText(t);
                Dialogs.info(TmUi.detailWin, "已复制到剪贴板");
            });
            box.add(TmUi.detailBody);
            box.add(copy);
            TmUi.detailWin.setContent(box);
            TmUi.detailWin.show();
        } else {
            TmUi.detailWin.activate();
        }
        fillDetail(r);
    }

    static void fillDetail(AmProcRow r) {
        if (r == null || r.sample == null || TmUi.detailBody == null) {
            return;
        }
        TmUi.detailPid = r.sample.pid;
        TmUi.detailStartSec = r.sample.startSec;
        TmUi.detailStartUsec = r.sample.startUsec;
        String path = r.sample.path;
        if (path == null || path.length() == 0 || (r.sample.flags & AmFlags.PATH) == 0) {
            path = "N/A";
        }
        String mem = "N/A";
        if ((r.sample.flags & AmFlags.FOOTPRINT) != 0) {
            mem = AmFormat.bytes(r.sample.footprint);
        } else if ((r.sample.flags & AmFlags.RSS) != 0) {
            mem = AmFormat.bytes(r.sample.rss);
        }
        String prot = "";
        if (AmProtect.isProtected(r.sample) != 0) {
            prot = "\n受保护：" + AmProtect.reason(r.sample);
        }
        String text = "名称\t" + r.sample.name + "\n"
                + "PID\t" + String.valueOf(r.sample.pid) + "\n"
                + "PPID\t" + String.valueOf(r.sample.ppid) + "\n"
                + "用户\t" + r.sample.user + "\n"
                + "CPU\t" + AmFormat.cpuPct(r.cpuMilli) + "\n"
                + "内存\t" + mem + "\n"
                + "线程\t" + AmFormat.threads(r.sample) + "\n"
                + "路径\t" + path + "\n"
                + "启动\t" + AmParse.longToString(r.sample.startSec) + "."
                + String.valueOf(r.sample.startUsec)
                + prot;
        TmUi.detailBody.setText(text);
        TmUi.detailCopyText = text;
    }

    static void refreshDetailIfOpen() {
        if (TmUi.detailWin == null || TmUi.detailBody == null || TmUi.detailPid <= 0) {
            return;
        }
        AmProcRow r = TmUi.state.findByIdentity(TmUi.detailPid, TmUi.detailStartSec, TmUi.detailStartUsec);
        if (r == null) {
            TmUi.detailBody.setText("所选进程已退出或身份失效");
            return;
        }
        fillDetail(r);
    }

    static void confirmTerminate() {
        AmProcRow r = selectedRow();
        if (r == null) {
            Dialogs.info(TmUi.window, "请先选择一个进程");
            return;
        }
        if (AmProtect.isProtected(r.sample) != 0) {
            Dialogs.error(TmUi.window, "无法结束：" + AmProtect.reason(r.sample));
            return;
        }
        String msg = "是否结束进程 " + r.sample.name + "（PID "
                + String.valueOf(r.sample.pid) + "）？\n将校验启动时间以防 PID 复用。";
        if (!Dialogs.confirm(TmUi.window, msg)) {
            return;
        }
        AmProcSample target = r.sample;
        int code = AmKill.terminate(target, AmKill.SIGKILL);
        if (code != 0) {
            Dialogs.error(TmUi.window, AmKill.describeError(code));
            return;
        }
        TmUi.tableDirty = 1;
        requestSample(1);
    }
}
