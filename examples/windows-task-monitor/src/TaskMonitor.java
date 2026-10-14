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
    static TextField searchField;
    static Label footPrimary;
    static Label footStatus;
    static Button pageProcBtn;
    static Button pagePerfBtn;
    static Button pageDetailBtn;
    static Button pauseBtn;
    static Button refreshBtn;
    static Button detailBtn;
    static Button termBtn;
    static VBox pageProc;
    static VBox pagePerf;
    static VBox pageDetail;
    static Label perfCpuLabel;
    static Label perfMemLabel;
    static Label perfMetaLabel;
    static TrendChart cpuChart;
    static TrendChart memChart;
    static Label detailPageBody;
    static BindingScope scope;
    static AmViewState state;
    static ArrayList<AmProcRow> viewRows;
    static ArrayList<int> cpuHistory;
    static ArrayList<int> memHistory;
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
    static int samplerBusy;
    static int pendingRefresh;
    static int tableDirty;
    static long lastTableRebuildNs;
    static long lastOkMonoNs;
    static int lastSampleOk;
    static int page; /* 0=proc 1=perf 2=detail */
    static int chromeBg;
    static int tableBg;
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
        if (memHistory == null) {
            memHistory = new ArrayList<int>();
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
        chromeBg = Component.rgba(236, 238, 241, 255);
        tableBg = Component.rgba(255, 255, 255, 255);
        alive = 1;
        paused = 0;
        samplerBusy = 0;
        pendingRefresh = 0;
        lastSampleOk = 0;
        lastOkMonoNs = 0;
        page = 0;
        selPid = -1;
        detailPid = -1;
    }
}

class TmWindow {
    static void show() {
        TmUi.ensure();
        TmUi.window = new Window("JNative Task Monitor", 1280, 820);
        TmUi.window.setMinSize(880, 520);
        TmUi.window.onClose(() -> {
            TmUi.alive = 0;
            TmUi.sampleToken = TmUi.sampleToken + 1;
            TmUi.refreshGen = TmUi.refreshGen + 1;
            if (TmUi.cpuHistory != null) {
                TmUi.cpuHistory.clear();
            }
            if (TmUi.memHistory != null) {
                TmUi.memHistory.clear();
            }
            if (TmUi.scope != null) {
                TmUi.scope.dispose();
                TmUi.scope = null;
            }
        });

        VBox root = new VBox();
        root.spacing = 0;
        root.padding = 0;
        root.setBackgroundRgba(TmUi.chromeBg);

        HBox toolbar = new HBox();
        toolbar.spacing = Theme.SPACE_2;
        toolbar.padding = Theme.SPACE_3;
        toolbar.prefHeight = 48;
        toolbar.height = 48;
        toolbar.setBackgroundRgba(TmUi.chromeBg);

        TmUi.pageProcBtn = new Button("进程");
        TmUi.pageProcBtn.setPrefSize(64, 28);
        TmUi.pageProcBtn.setChromeStyle(3);
        TmUi.pageProcBtn.onClick(() -> { TmActions.showPage(0); });
        TmUi.pagePerfBtn = new Button("性能");
        TmUi.pagePerfBtn.setPrefSize(64, 28);
        TmUi.pagePerfBtn.setChromeStyle(3);
        TmUi.pagePerfBtn.onClick(() -> { TmActions.showPage(1); });
        TmUi.pageDetailBtn = new Button("详情");
        TmUi.pageDetailBtn.setPrefSize(64, 28);
        TmUi.pageDetailBtn.setChromeStyle(3);
        TmUi.pageDetailBtn.onClick(() -> { TmActions.showPage(2); });

        TmUi.pauseBtn = new Button("暂停");
        TmUi.pauseBtn.setPrefSize(64, 28);
        TmUi.pauseBtn.setChromeStyle(3);
        TmUi.pauseBtn.onClick(() -> { TmActions.togglePause(); });
        TmUi.refreshBtn = new Button("刷新");
        TmUi.refreshBtn.setPrefSize(64, 28);
        TmUi.refreshBtn.setChromeStyle(3);
        TmUi.refreshBtn.onClick(() -> { TmActions.requestSample(1); });

        TmUi.detailBtn = new Button("打开详情");
        TmUi.detailBtn.setPrefSize(88, 28);
        TmUi.detailBtn.setChromeStyle(3);
        TmUi.detailBtn.onClick(() -> { TmActions.showDetail(); });
        TmUi.termBtn = new Button("结束进程");
        TmUi.termBtn.setPrefSize(88, 28);
        TmUi.termBtn.setChromeStyle(3);
        TmUi.termBtn.onClick(() -> { TmActions.confirmTerminate(); });
        TmUi.termBtn.setEnabled(false);
        TmUi.detailBtn.setEnabled(false);

        TmUi.searchField = new TextField("");
        TmUi.searchField.setPrefSize(240, 28);
        TmUi.searchField.setBackgroundRgba(Component.rgba(255, 255, 255, 255));
        TmUi.searchField.setForegroundRgba(Component.rgba(30, 30, 30, 255));
        TmUi.searchField.onChange((ev) -> { TmActions.onSearchChanged(); });
        Label searchHint = new Label("搜索");
        Theme.applyStyle(searchHint, Theme.STYLE_SECONDARY);
        searchHint.setPrefSize(36, 26);
        Label flex = new Label("");
        flex.hgrow = 1;
        flex.setBackgroundRgba(TmUi.chromeBg);

        toolbar.add(TmUi.pageProcBtn);
        toolbar.add(TmUi.pagePerfBtn);
        toolbar.add(TmUi.pageDetailBtn);
        toolbar.add(TmUi.pauseBtn);
        toolbar.add(TmUi.refreshBtn);
        toolbar.add(TmUi.detailBtn);
        toolbar.add(TmUi.termBtn);
        toolbar.add(flex);
        toolbar.add(searchHint);
        toolbar.add(TmUi.searchField);

        /* ---- Process page ---- */
        TmUi.pageProc = new VBox();
        TmUi.pageProc.spacing = 0;
        TmUi.pageProc.padding = 0;
        TmUi.pageProc.vgrow = 1;
        TmUi.pageProc.setBackgroundRgba(TmUi.tableBg);
        TmUi.table = new TableView<AmProcRow>();
        TmUi.table.vgrow = 1;
        TmUi.table.prefHeight = 600;
        TmUi.table.height = 600;
        TmUi.table.setBackgroundRgba(TmUi.tableBg);
        TmUi.table.setRowIdProvider(new TmRowIds());
        TmUi.table.setEmptyMessage("正在采样…");
        TmActions.rebuildColumns();
        TmUi.table.onSelectionChanged((ev) -> { TmActions.onSelect(ev.selectedIndex); });
        TmUi.table.onDoubleClick((ev) -> { TmActions.showDetail(); });
        TmUi.table.onHeaderClick((ev) -> { TmActions.sort(ev.selectedIndex); });
        TmUi.pageProc.add(TmUi.table);

        /* ---- Performance page ---- */
        TmUi.pagePerf = new VBox();
        TmUi.pagePerf.spacing = Theme.SPACE_3;
        TmUi.pagePerf.padding = Theme.SPACE_4;
        TmUi.pagePerf.vgrow = 1;
        TmUi.pagePerf.setBackgroundRgba(TmUi.tableBg);
        TmUi.pagePerf.setVisible(false);
        TmUi.perfCpuLabel = new Label("CPU —");
        TmUi.perfCpuLabel.setPrefSize(700, 28);
        Theme.applyStyle(TmUi.perfCpuLabel, Theme.STYLE_BODY);
        TmUi.cpuChart = new TrendChart();
        TmUi.cpuChart.setCapacity(60);
        TmUi.cpuChart.setYRange(0.0, 100.0);
        TmUi.cpuChart.setPrefSize(1100, 120);
        TmUi.cpuChart.width = 1100;
        TmUi.cpuChart.height = 120;
        TmUi.perfMemLabel = new Label("内存 —");
        TmUi.perfMemLabel.setPrefSize(700, 28);
        Theme.applyStyle(TmUi.perfMemLabel, Theme.STYLE_BODY);
        TmUi.memChart = new TrendChart();
        TmUi.memChart.setCapacity(60);
        TmUi.memChart.setYRange(0.0, 100.0);
        TmUi.memChart.setPrefSize(1100, 120);
        TmUi.memChart.width = 1100;
        TmUi.memChart.height = 120;
        TmUi.perfMetaLabel = new Label("最近约 60 秒 · 间隔 1000 ms · CPU% 按整机逻辑处理器归一化 · 内存为系统已用估计");
        TmUi.perfMetaLabel.setPrefSize(1100, 24);
        Theme.applyStyle(TmUi.perfMetaLabel, Theme.STYLE_SECONDARY);
        TmUi.pagePerf.add(TmUi.perfCpuLabel);
        TmUi.pagePerf.add(TmUi.cpuChart);
        TmUi.pagePerf.add(TmUi.perfMemLabel);
        TmUi.pagePerf.add(TmUi.memChart);
        TmUi.pagePerf.add(TmUi.perfMetaLabel);

        /* ---- Detail page ---- */
        TmUi.pageDetail = new VBox();
        TmUi.pageDetail.spacing = Theme.SPACE_2;
        TmUi.pageDetail.padding = Theme.SPACE_4;
        TmUi.pageDetail.vgrow = 1;
        TmUi.pageDetail.setBackgroundRgba(TmUi.tableBg);
        TmUi.pageDetail.setVisible(false);
        TmUi.detailPageBody = new Label("在进程页选择一个进程以查看详情。");
        TmUi.detailPageBody.setPrefSize(900, 400);
        Theme.applyStyle(TmUi.detailPageBody, Theme.STYLE_BODY);
        TmUi.pageDetail.add(TmUi.detailPageBody);

        HBox footer = new HBox();
        footer.spacing = Theme.SPACE_3;
        footer.padding = Theme.SPACE_3;
        footer.prefHeight = 40;
        footer.height = 40;
        footer.setBackgroundRgba(TmUi.chromeBg);
        TmUi.footPrimary = new Label("系统 CPU —  ·  等待采样");
        TmUi.footPrimary.setPrefSize(720, 26);
        Theme.applyStyle(TmUi.footPrimary, Theme.STYLE_BODY);
        TmUi.footStatus = new Label("就绪");
        TmUi.footStatus.setPrefSize(280, 26);
        Theme.applyStyle(TmUi.footStatus, Theme.STYLE_SECONDARY);
        Label fflex = new Label("");
        fflex.hgrow = 1;
        fflex.setBackgroundRgba(TmUi.chromeBg);
        footer.add(TmUi.footPrimary);
        footer.add(fflex);
        footer.add(TmUi.footStatus);

        root.add(toolbar);
        root.add(TmUi.pageProc);
        root.add(TmUi.pagePerf);
        root.add(TmUi.pageDetail);
        root.add(footer);
        TmUi.window.setContent(root);
        TmUi.window.show();
        TmActions.showPage(0);
        TmActions.syncToolbarEnabled();
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
            return "不可用";
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
            return "不可用";
        }
        return String.valueOf(row.sample.pid);
    }
}

class TmColUser implements ColumnRenderer<AmProcRow> {
    String getTitle() {
        return TmActions.colTitle(2, "用户");
    }
    String renderRow(AmProcRow row) {
        if (row == null || row.sample == null || (row.sample.flags & AmFlags.USER) == 0
                || row.sample.user == null) {
            return "不可用";
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
            return AmFormat.waiting();
        }
        return AmFormat.cpuPct(row.cpuMilli);
    }
}

class TmColMem implements ColumnRenderer<AmProcRow> {
    String getTitle() {
        return TmActions.colTitle(4, "工作集");
    }
    String renderRow(AmProcRow row) {
        if (row == null || row.sample == null) {
            return "不可用";
        }
        if ((row.sample.flags & AmFlags.FOOTPRINT) != 0) {
            return AmFormat.bytes(row.sample.footprint);
        }
        if ((row.sample.flags & AmFlags.RSS) != 0) {
            return AmFormat.bytes(row.sample.rss);
        }
        return "不可用";
    }
}

class TmColThr implements ColumnRenderer<AmProcRow> {
    String getTitle() {
        return TmActions.colTitle(5, "线程");
    }
    String renderRow(AmProcRow row) {
        if (row == null || row.sample == null) {
            return "不可用";
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
        /* Name flex but modest preferred width so numeric cols stay visible. */
        TmUi.table.addColumnDef(colTitle(0, "进程名称"), new TmColName(), 220, "L", 1);
        TmUi.table.addColumnDef(colTitle(1, "PID"), new TmColPid(), 80, "R", 0);
        TmUi.table.addColumnDef(colTitle(2, "用户"), new TmColUser(), 120, "L", 0);
        TmUi.table.addColumnDef(colTitle(3, "CPU %"), new TmColCpu(), 90, "R", 0);
        TmUi.table.addColumnDef(colTitle(4, "工作集"), new TmColMem(), 110, "R", 0);
        TmUi.table.addColumnDef(colTitle(5, "线程"), new TmColThr(), 72, "R", 0);
    }

    static void showPage(int p) {
        if (p < 0 || p > 2) {
            return;
        }
        TmUi.page = p;
        if (TmUi.pageProc != null) {
            TmUi.pageProc.setVisible(p == 0);
        }
        if (TmUi.pagePerf != null) {
            TmUi.pagePerf.setVisible(p == 1);
        }
        if (TmUi.pageDetail != null) {
            TmUi.pageDetail.setVisible(p == 2);
        }
        if (p == 1) {
            updatePerfPage();
        }
        if (p == 2) {
            refreshDetailPage();
        }
        if (TmUi.window != null && TmUi.window.content != null) {
            TmUi.window.content.layout();
        }
    }

    static void togglePause() {
        if (TmUi.paused != 0) {
            TmUi.paused = 0;
            if (TmUi.pauseBtn != null) {
                TmUi.pauseBtn.setText("暂停");
            }
            requestSample(0);
        } else {
            TmUi.paused = 1;
            if (TmUi.pauseBtn != null) {
                TmUi.pauseBtn.setText("恢复");
            }
        }
        updateStatusBar();
    }

    static void onSearchChanged() {
        String q = "";
        if (TmUi.searchField != null) {
            q = TmUi.searchField.getText();
        }
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
            syncToolbarEnabled();
            refreshDetailPage();
            return;
        }
        AmProcRow r = TmUi.viewRows.get(index);
        if (r == null || r.sample == null) {
            TmUi.selPid = -1;
            syncToolbarEnabled();
            refreshDetailPage();
            return;
        }
        TmUi.selPid = r.sample.pid;
        TmUi.selStartSec = r.sample.startSec;
        TmUi.selStartUsec = r.sample.startUsec;
        syncToolbarEnabled();
        refreshDetailIfOpen();
        refreshDetailPage();
    }

    static void syncToolbarEnabled() {
        AmProcRow r = selectedRow();
        int ok = 0;
        if (r != null && r.sample != null) {
            ok = 1;
        }
        if (TmUi.detailBtn != null) {
            TmUi.detailBtn.setEnabled(ok != 0);
        }
        int canTerm = 0;
        if (ok != 0 && AmProtect.isProtected(r.sample) == 0) {
            canTerm = 1;
        }
        if (TmUi.termBtn != null) {
            TmUi.termBtn.setEnabled(canTerm != 0);
        }
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
        TmUi.samplerBusy = 1;
        int token = TmUi.sampleToken;
        Thread t = new Thread(() -> {
            AmSnapshot a = AmSampler.take();
            try {
                Thread.sleep(250);
            } catch (Exception e) {
            }
            AmSnapshot b = AmSampler.take();
            Application.invokeLater(() -> {
                TmUi.samplerBusy = 0;
                if (TmUi.alive == 0 || token != TmUi.sampleToken) {
                    return;
                }
                int ok = 0;
                if (a != null) {
                    applyOneSnapshot(a);
                    ok = 1;
                }
                if (b != null) {
                    applyOneSnapshot(b);
                    ok = 1;
                }
                if (ok != 0) {
                    TmUi.lastSampleOk = 1;
                    TmUi.lastOkMonoNs = AmNative.monoNs();
                } else {
                    TmUi.lastSampleOk = 0;
                    if (TmUi.state != null) {
                        TmUi.state.lastError = "采样失败";
                    }
                }
                finishSampleUi(1);
                armRefresh();
                drainPending();
            });
        });
        t.start();
    }

    static void drainPending() {
        if (TmUi.alive == 0) {
            return;
        }
        if (TmUi.pendingRefresh != 0 && TmUi.samplerBusy == 0) {
            TmUi.pendingRefresh = 0;
            requestSample(0);
        }
    }

    static void applyOneSnapshot(AmSnapshot snap) {
        if (snap == null) {
            return;
        }
        TmUi.state.applySnapshot(snap);
        if (TmUi.paused != 0) {
            return;
        }
        if (TmUi.state.sysCpuMilli >= 0) {
            TmUi.cpuHistory.add(TmUi.state.sysCpuMilli);
            while (TmUi.cpuHistory.size() > 60) {
                TmUi.cpuHistory.removeAt(0);
            }
        }
        if (TmUi.state.sysMemUsedBytes >= 0 && TmUi.state.memTotal > 0) {
            int memMilli = (int)((TmUi.state.sysMemUsedBytes * 10000L) / TmUi.state.memTotal);
            TmUi.memHistory.add(memMilli);
            while (TmUi.memHistory.size() > 60) {
                TmUi.memHistory.removeAt(0);
            }
        }
    }

    static void finishSampleUi(int forceTable) {
        updateFooter();
        updateStatusBar();
        updatePerfPage();
        if (TmUi.state != null && TmUi.state.sampleCount >= 2) {
            if (forceTable != 0 || TmUi.tableDirty != 0 || TmUi.lastTableRebuildNs == 0) {
                maybeRebuildTable(1);
            }
        }
        syncToolbarEnabled();
        refreshDetailIfOpen();
        if (TmUi.page == 2) {
            refreshDetailPage();
        }
    }

    static void requestSample(int force) {
        if (TmUi.alive == 0) {
            return;
        }
        if (TmUi.samplerBusy != 0) {
            if (force != 0) {
                TmUi.pendingRefresh = 1;
            } else {
                TmUi.pendingRefresh = 1;
            }
            return;
        }
        if (AmSampler.inFlight != 0 && force == 0) {
            TmUi.pendingRefresh = 1;
            return;
        }
        int token = TmUi.sampleToken;
        TmUi.samplerBusy = 1;
        Thread t = new Thread(() -> {
            AmSnapshot snap = AmSampler.take();
            Application.invokeLater(() -> {
                TmUi.samplerBusy = 0;
                if (TmUi.alive == 0 || token != TmUi.sampleToken) {
                    return;
                }
                if (snap != null) {
                    applyOneSnapshot(snap);
                    TmUi.lastSampleOk = 1;
                    TmUi.lastOkMonoNs = AmNative.monoNs();
                    if (TmUi.state != null) {
                        TmUi.state.lastError = "";
                    }
                } else {
                    TmUi.lastSampleOk = 0;
                    if (TmUi.state != null) {
                        TmUi.state.lastError = "采样失败";
                    }
                }
                /* Periodic tick: update footer/charts only unless dirty/forced. */
                int forceTable = 0;
                if (force != 0 || TmUi.tableDirty != 0 || TmUi.lastTableRebuildNs == 0) {
                    forceTable = 1;
                }
                finishSampleUi(forceTable);
                drainPending();
            });
        });
        t.start();
    }

    static void armRefresh() {
        if (TmUi.alive == 0) {
            return;
        }
        TmUi.refreshGen = TmUi.refreshGen + 1;
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
        } else if (TmUi.state.sampleCount < 2) {
            cpu = AmFormat.waiting();
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
        String mem = "";
        if (TmUi.state.sysMemUsedBytes >= 0) {
            mem = "  ·  内存 " + AmFormat.bytes(TmUi.state.sysMemUsedBytes);
            if (TmUi.state.memTotal > 0) {
                mem = mem + " / " + AmFormat.bytes(TmUi.state.memTotal);
            }
        }
        TmUi.footPrimary.setText("系统 CPU " + cpu
                + "  ·  进程 " + String.valueOf(nproc)
                + "  ·  线程 " + String.valueOf(nthr) + mem);
    }

    static void updateStatusBar() {
        if (TmUi.footStatus == null) {
            return;
        }
        String st = "";
        if (TmUi.paused != 0) {
            st = "已暂停";
        } else if (TmUi.samplerBusy != 0) {
            st = "采样中…";
        } else {
            st = "刷新 " + String.valueOf(TmUi.refreshMs) + " ms";
        }
        if (TmUi.lastSampleOk != 0 && TmUi.lastOkMonoNs > 0) {
            long ageMs = (AmNative.monoNs() - TmUi.lastOkMonoNs) / 1000000L;
            if (ageMs < 0) {
                ageMs = 0;
            }
            st = st + "  ·  上次成功 " + AmParse.longToString(ageMs) + " ms 前";
        } else if (TmUi.state != null && TmUi.state.lastError != null
                && TmUi.state.lastError.length() > 0) {
            st = st + "  ·  数据已过期（" + TmUi.state.lastError + "）";
        }
        TmUi.footStatus.setText(st);
    }

    static void updatePerfPage() {
        if (TmUi.perfCpuLabel == null) {
            return;
        }
        String cpu = "—";
        if (TmUi.state != null && TmUi.state.sysCpuMilli >= 0) {
            cpu = AmFormat.cpuPct(TmUi.state.sysCpuMilli);
        } else if (TmUi.state != null && TmUi.state.sampleCount < 2) {
            cpu = AmFormat.waiting();
        }
        int ncpu = 0;
        if (TmUi.state != null) {
            ncpu = TmUi.state.ncpu;
        }
        TmUi.perfCpuLabel.setText("CPU 利用率 " + cpu
                + (ncpu > 0 ? ("  ·  逻辑处理器 " + String.valueOf(ncpu)) : ""));
        if (TmUi.perfMemLabel != null && TmUi.state != null) {
            String mu = "—";
            String mt = "—";
            if (TmUi.state.sysMemUsedBytes >= 0) {
                mu = AmFormat.bytes(TmUi.state.sysMemUsedBytes);
            }
            if (TmUi.state.memTotal > 0) {
                mt = AmFormat.bytes(TmUi.state.memTotal);
            }
            TmUi.perfMemLabel.setText("内存已用 " + mu + " / 总量 " + mt + "（系统页计数估计）");
        }
        if (TmUi.cpuChart != null && TmUi.cpuHistory != null) {
            TmUi.cpuChart.setSamplesFromMilli(TmUi.cpuHistory, 0.01);
        }
        if (TmUi.memChart != null && TmUi.memHistory != null) {
            TmUi.memChart.setSamplesFromMilli(TmUi.memHistory, 0.01);
        }
        if (TmUi.perfMetaLabel != null) {
            String pauseNote = "";
            if (TmUi.paused != 0) {
                pauseNote = " · 已暂停（曲线不再推进）";
            }
            TmUi.perfMetaLabel.setText("最近约 "
                    + String.valueOf(TmUi.cpuHistory != null ? TmUi.cpuHistory.size() : 0)
                    + " 秒 · 间隔 " + String.valueOf(TmUi.refreshMs)
                    + " ms · CPU% 按整机逻辑处理器归一化 · 内存为系统已用估计"
                    + pauseNote);
        }
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
                    rr.sample.user = "不可用";
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
        syncToolbarEnabled();
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
            String nx = x.sample.name != null ? x.sample.name : "";
            String ny = y.sample.name != null ? y.sample.name : "";
            c = nx.compareTo(ny);
        } else if (col == 1) {
            c = x.sample.pid - y.sample.pid;
        } else if (col == 2) {
            String ux = x.sample.user != null ? x.sample.user : "";
            String uy = y.sample.user != null ? y.sample.user : "";
            c = ux.compareTo(uy);
        } else if (col == 3) {
            c = x.cpuMilli - y.cpuMilli;
        } else if (col == 4) {
            long mx = -1;
            long my = -1;
            if ((x.sample.flags & AmFlags.FOOTPRINT) != 0) {
                mx = x.sample.footprint;
            } else if ((x.sample.flags & AmFlags.RSS) != 0) {
                mx = x.sample.rss;
            }
            if ((y.sample.flags & AmFlags.FOOTPRINT) != 0) {
                my = y.sample.footprint;
            } else if ((y.sample.flags & AmFlags.RSS) != 0) {
                my = y.sample.rss;
            }
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

    static String detailText(AmProcRow r) {
        if (r == null || r.sample == null) {
            return "在进程页选择一个进程以查看详情。";
        }
        String path = r.sample.path;
        if (path == null || path.length() == 0 || (r.sample.flags & AmFlags.PATH) == 0) {
            path = "不可用";
        }
        String mem = "不可用";
        if ((r.sample.flags & AmFlags.FOOTPRINT) != 0) {
            mem = AmFormat.bytes(r.sample.footprint) + "（工作集）";
        } else if ((r.sample.flags & AmFlags.RSS) != 0) {
            mem = AmFormat.bytes(r.sample.rss) + "（工作集）";
        }
        String user = "不可用";
        if ((r.sample.flags & AmFlags.USER) != 0 && r.sample.user != null) {
            user = r.sample.user;
        }
        String prot = "";
        if (AmProtect.isProtected(r.sample) != 0) {
            prot = "\n受保护：" + AmProtect.reason(r.sample);
        }
        return "名称\t" + r.sample.name + "\n"
                + "PID\t" + String.valueOf(r.sample.pid) + "\n"
                + "PPID\t" + String.valueOf(r.sample.ppid) + "\n"
                + "用户\t" + user + "\n"
                + "CPU\t" + AmFormat.cpuPct(r.cpuMilli) + "\n"
                + "工作集\t" + mem + "\n"
                + "线程\t" + AmFormat.threads(r.sample) + "\n"
                + "路径\t" + path + "\n"
                + "启动\t" + AmParse.longToString(r.sample.startSec) + "."
                + String.valueOf(r.sample.startUsec)
                + prot;
    }

    static void refreshDetailPage() {
        if (TmUi.detailPageBody == null) {
            return;
        }
        AmProcRow r = selectedRow();
        if (r == null) {
            TmUi.detailPageBody.setText("在进程页选择一个进程以查看详情。");
            return;
        }
        TmUi.detailPageBody.setText(detailText(r));
    }

    static void showDetail() {
        AmProcRow r = selectedRow();
        if (r == null) {
            Dialogs.info(TmUi.window, "请先选择一个进程");
            return;
        }
        showPage(2);
        if (TmUi.detailWin == null) {
            TmUi.detailWin = new Window("进程详情", 480, 420);
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
            TmUi.detailBody.setPrefSize(440, 300);
            Button copy = new Button("复制");
            copy.setChromeStyle(3);
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
        String text = detailText(r);
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
        int idOk = AmNative.checkIdentity(target.pid, target.startSec, target.startUsec);
        if (idOk == 0) {
            Dialogs.error(TmUi.window, "目标已退出或 PID 已被复用，未执行结束。");
            TmUi.tableDirty = 1;
            requestSample(1);
            return;
        }
        if (idOk < 0) {
            Dialogs.error(TmUi.window, "无法校验进程身份（权限不足或 API 失败）。");
            return;
        }
        int code = AmKill.terminate(target, AmKill.SIGKILL);
        if (code != 0) {
            Dialogs.error(TmUi.window, AmKill.describeError(code));
            return;
        }
        TmUi.tableDirty = 1;
        requestSample(1);
    }
}
