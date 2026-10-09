/**
 * macOS Activity Monitor — native desktop UI (jnative.ui).
 * Core sampling lives in AmCore.java (merged at build time).
 *
 * @author 张晓达
 */
public class ActivityMonitor {
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
        Theme.setAppearanceMode(Theme.MODE_LIGHT);
        Application app = new Application();
        AmWindow.show();
        app.run();
    }
}

class AmUi {
    static Window window;
    static Label overview;
    static Label status;
    static TextField search;
    static Button pauseBtn;
    static Button refreshBtn;
    static Button detailBtn;
    static Button termBtn;
    static Button killBtn;
    static TableView<AmProcRow> table;
    static Label detail;
    static AmViewState state;
    static int alive;
    static int paused;
    static int refreshMs;
    static int sampleToken;
    static int selPid;
    static long selStartSec;
    static int selStartUsec;
    static String searchText;
    static int sortCol;
    static int sortAsc;
    static ArrayList<AmProcRow> viewRows;

    static void ensure() {
        if (state == null) {
            state = new AmViewState();
        }
        if (refreshMs <= 0) {
            refreshMs = 1500;
        }
        alive = 1;
        paused = 0;
        sampleToken = 0;
        selPid = -1;
        selStartSec = -1;
        selStartUsec = -1;
        searchText = "";
        sortCol = 3; /* CPU */
        sortAsc = 0; /* desc */
        viewRows = new ArrayList<AmProcRow>();
    }
}

class AmWindow {
    static void show() {
        AmUi.ensure();
        AmUi.window = new Window(AmI18n.t("title"), 1100, 720);
        AmUi.window.onClose(() -> {
            AmUi.alive = 0;
            AmUi.sampleToken = AmUi.sampleToken + 1;
        });

        VBox root = new VBox();
        root.spacing = 8;
        root.padding = 12;

        AmUi.overview = new Label(AmI18n.t("overview_wait"));
        AmUi.overview.setDisplayStyle(13, 0, -14935529);
        AmUi.overview.setPrefSize(1060, 40);

        HBox tools = new HBox();
        tools.spacing = 8;
        AmUi.search = new TextField("");
        AmUi.search.setPrefSize(280, 28);
        AmUi.pauseBtn = new Button(AmI18n.t("pause"));
        AmUi.refreshBtn = new Button(AmI18n.t("refresh_now"));
        AmUi.detailBtn = new Button(AmI18n.t("detail"));
        AmUi.termBtn = new Button(AmI18n.t("term"));
        AmUi.killBtn = new Button(AmI18n.t("kill"));
        AmUi.status = new Label(AmI18n.t("status_ready"));
        AmUi.status.setDisplayStyle(12, 0, -10725812);
        AmUi.status.setPrefSize(320, 28);

        AmUi.pauseBtn.onClick(() -> { AmActions.togglePause(); });
        AmUi.refreshBtn.onClick(() -> { AmActions.requestSample(1); });
        AmUi.detailBtn.onClick(() -> { AmActions.showDetail(); });
        AmUi.termBtn.onClick(() -> { AmActions.confirmKill(0); });
        AmUi.killBtn.onClick(() -> { AmActions.confirmKill(1); });

        tools.add(AmUi.search);
        tools.add(AmUi.pauseBtn);
        tools.add(AmUi.refreshBtn);
        tools.add(AmUi.detailBtn);
        tools.add(AmUi.termBtn);
        tools.add(AmUi.killBtn);
        tools.add(AmUi.status);

        AmUi.table = new TableView<AmProcRow>();
        AmUi.table.setPrefSize(1060, 480);
        AmUi.table.addColumn(new AmColPid());
        AmUi.table.addColumn(new AmColName());
        AmUi.table.addColumn(new AmColUser());
        AmUi.table.addColumn(new AmColCpu());
        AmUi.table.addColumn(new AmColRss());
        AmUi.table.addColumn(new AmColFoot());
        AmUi.table.addColumn(new AmColThr());
        AmUi.table.onSelectionChanged((ev) -> {
            AmActions.onSelect(ev.selectedIndex);
        });

        HBox sortBar = new HBox();
        sortBar.spacing = 6;
        Button sPid = new Button(AmI18n.t("sort_pid"));
        Button sName = new Button(AmI18n.t("sort_name"));
        Button sCpu = new Button(AmI18n.t("sort_cpu"));
        Button sMem = new Button(AmI18n.t("sort_rss"));
        sPid.onClick(() -> { AmActions.sort(0); });
        sName.onClick(() -> { AmActions.sort(1); });
        sCpu.onClick(() -> { AmActions.sort(3); });
        sMem.onClick(() -> { AmActions.sort(4); });
        sortBar.add(sPid);
        sortBar.add(sName);
        sortBar.add(sCpu);
        sortBar.add(sMem);

        AmUi.detail = new Label(AmI18n.t("detail_hint"));
        AmUi.detail.setDisplayStyle(12, 0, -10725812);
        AmUi.detail.setPrefSize(1060, 72);

        Label metricsHint = new Label(AmI18n.t("metrics_hint"));
        metricsHint.setDisplayStyle(11, 0, -10725812);
        metricsHint.setPrefSize(1060, 36);

        root.add(AmUi.overview);
        root.add(tools);
        root.add(sortBar);
        root.add(AmUi.table);
        root.add(AmUi.detail);
        root.add(metricsHint);

        AmUi.window.setContent(root);
        AmUi.window.show();
        AmActions.setStatus(AmI18n.t("status_sampling"), 0);
        AmActions.requestSample(0);
        AmActions.scheduleLoop();
    }
}

class AmColPid implements ColumnRenderer<AmProcRow> {
    String getTitle() { return AmI18n.t("col_pid"); }
    String renderRow(AmProcRow r) {
        if (r == null || r.sample == null) {
            return AmFormat.na();
        }
        String s = String.valueOf(r.sample.pid);
        while (s.length() < 7) {
            s = " " + s;
        }
        return s;
    }
}

class AmColName implements ColumnRenderer<AmProcRow> {
    String getTitle() { return AmI18n.t("col_name"); }
    String renderRow(AmProcRow r) {
        if (r == null || r.sample == null) {
            return AmFormat.na();
        }
        if ((r.sample.flags & AmFlags.NAME) == 0) {
            return AmFormat.na();
        }
        if (r.sample.name == null) {
            return AmFormat.na();
        }
        return r.sample.name;
    }
}

class AmColUser implements ColumnRenderer<AmProcRow> {
    String getTitle() { return AmI18n.t("col_user"); }
    String renderRow(AmProcRow r) {
        if (r == null || r.sample == null) {
            return AmFormat.na();
        }
        if ((r.sample.flags & AmFlags.USER) == 0) {
            return AmFormat.na();
        }
        if (r.sample.user == null) {
            return AmFormat.na();
        }
        return r.sample.user;
    }
}

class AmColCpu implements ColumnRenderer<AmProcRow> {
    String getTitle() { return AmI18n.t("col_cpu"); }
    String renderRow(AmProcRow r) {
        if (r == null) {
            return AmFormat.na();
        }
        return AmFormat.cpuSortKey(r.cpuMilli) + " " + AmFormat.cpuPct(r.cpuMilli);
    }
}

class AmColRss implements ColumnRenderer<AmProcRow> {
    String getTitle() { return AmI18n.t("col_rss"); }
    String renderRow(AmProcRow r) {
        if (r == null || r.sample == null) {
            return AmFormat.na();
        }
        if ((r.sample.flags & AmFlags.RSS) == 0) {
            return AmFormat.na();
        }
        return AmFormat.bytesSortKey(r.sample.rss) + " " + AmFormat.bytes(r.sample.rss);
    }
}

class AmColFoot implements ColumnRenderer<AmProcRow> {
    String getTitle() { return AmI18n.t("col_foot"); }
    String renderRow(AmProcRow r) {
        if (r == null || r.sample == null) {
            return AmFormat.na();
        }
        if ((r.sample.flags & AmFlags.FOOTPRINT) == 0) {
            return AmFormat.na();
        }
        return AmFormat.bytesSortKey(r.sample.footprint) + " " + AmFormat.bytes(r.sample.footprint);
    }
}

class AmColThr implements ColumnRenderer<AmProcRow> {
    String getTitle() { return AmI18n.t("col_thr"); }
    String renderRow(AmProcRow r) {
        if (r == null || r.sample == null) {
            return AmFormat.na();
        }
        String t = AmFormat.threads(r.sample);
        if (t == null || t.equals(AmFormat.na())) {
            return AmFormat.na();
        }
        String s = t;
        while (s.length() < 5) {
            s = " " + s;
        }
        return s;
    }
}

class AmI18n {
    static String lang;

    static String t(String key) {
        if (lang == null) {
            lang = "zh";
            String env = Env.get("LANG");
            if (env != null && env.length() >= 2) {
                String a = env.substring(0, 2);
                if (a.equals("en")) {
                    lang = "en";
                }
            }
        }
        if (lang.equals("en")) {
            return en(key);
        }
        return zh(key);
    }

    static String zh(String key) {
        if (key.equals("title")) return "JNative 活动监视器";
        if (key.equals("overview_wait")) return "系统概览：等待首次采样…";
        if (key.equals("pause")) return "暂停刷新";
        if (key.equals("resume")) return "恢复刷新";
        if (key.equals("refresh_now")) return "立即刷新";
        if (key.equals("detail")) return "详情";
        if (key.equals("term")) return "结束进程";
        if (key.equals("kill")) return "强制结束";
        if (key.equals("status_ready")) return "就绪";
        if (key.equals("status_sampling")) return "正在采样…";
        if (key.equals("status_paused")) return "已暂停自动刷新";
        if (key.equals("detail_hint")) return "选中进程后可查看详情或结束进程。";
        if (key.equals("metrics_hint")) return "CPU：两次采样 CPU 时间差 / 单调时钟间隔，100%=1 逻辑核心。内存：RSS=pti_resident_size；物理占用=ri_phys_footprint。系统内存页来自 HOST_VM_INFO64，口径可能与“活动监视器”不同。";
        if (key.equals("col_pid")) return "PID";
        if (key.equals("col_name")) return "名称";
        if (key.equals("col_user")) return "用户";
        if (key.equals("col_cpu")) return "CPU%";
        if (key.equals("col_rss")) return "RSS";
        if (key.equals("col_foot")) return "物理占用";
        if (key.equals("col_thr")) return "线程";
        if (key.equals("sort_pid")) return "按 PID";
        if (key.equals("sort_name")) return "按名称";
        if (key.equals("sort_cpu")) return "按 CPU";
        if (key.equals("sort_rss")) return "按 RSS";
        return key;
    }

    static String en(String key) {
        if (key.equals("title")) return "JNative Activity Monitor";
        if (key.equals("overview_wait")) return "System overview: waiting for first sample…";
        if (key.equals("pause")) return "Pause";
        if (key.equals("resume")) return "Resume";
        if (key.equals("refresh_now")) return "Refresh";
        if (key.equals("detail")) return "Details";
        if (key.equals("term")) return "Quit Process";
        if (key.equals("kill")) return "Force Quit";
        if (key.equals("status_ready")) return "Ready";
        if (key.equals("status_sampling")) return "Sampling…";
        if (key.equals("status_paused")) return "Auto-refresh paused";
        if (key.equals("detail_hint")) return "Select a process for details or terminate.";
        if (key.equals("metrics_hint")) return "CPU: delta CPU time / monotonic interval; 100%=1 logical core. Memory: RSS=pti_resident_size; footprint=ri_phys_footprint. System pages from HOST_VM_INFO64.";
        if (key.equals("col_pid")) return "PID";
        if (key.equals("col_name")) return "Name";
        if (key.equals("col_user")) return "User";
        if (key.equals("col_cpu")) return "CPU%";
        if (key.equals("col_rss")) return "RSS";
        if (key.equals("col_foot")) return "Footprint";
        if (key.equals("col_thr")) return "Threads";
        if (key.equals("sort_pid")) return "Sort PID";
        if (key.equals("sort_name")) return "Sort Name";
        if (key.equals("sort_cpu")) return "Sort CPU";
        if (key.equals("sort_rss")) return "Sort RSS";
        return key;
    }
}

class AmActions {
    static void setStatus(String msg, int isError) {
        if (AmUi.alive == 0 || AmUi.status == null) {
            return;
        }
        AmUi.status.setText(msg);
        if (isError != 0) {
            AmUi.status.setDisplayStyle(12, 0, -65536);
        } else {
            AmUi.status.setDisplayStyle(12, 0, -10725812);
        }
    }

    static void togglePause() {
        if (AmUi.paused != 0) {
            AmUi.paused = 0;
            AmUi.pauseBtn.setText(AmI18n.t("pause"));
            setStatus(AmI18n.t("status_ready"), 0);
            requestSample(0);
        } else {
            AmUi.paused = 1;
            AmUi.pauseBtn.setText(AmI18n.t("resume"));
            setStatus(AmI18n.t("status_paused"), 0);
        }
    }

    static void scheduleLoop() {
        Thread t = new Thread(() -> {
            while (AmUi.alive != 0) {
                try {
                    Thread.sleep(AmUi.refreshMs);
                } catch (Exception e) {
                }
                if (AmUi.alive == 0) {
                    return;
                }
                if (AmUi.paused != 0) {
                    continue;
                }
                Application.invokeLater(() -> {
                    if (AmUi.alive == 0 || AmUi.paused != 0) {
                        return;
                    }
                    requestSample(0);
                });
            }
        });
        t.start();
    }

    static void requestSample(int force) {
        if (AmUi.alive == 0) {
            return;
        }
        if (AmSampler.inFlight != 0 && force == 0) {
            return;
        }
        AmUi.sampleToken = AmUi.sampleToken + 1;
        int token = AmUi.sampleToken;
        /* pull search text on UI thread */
        if (AmUi.search != null) {
            String q = AmUi.search.getText();
            if (q == null) {
                q = "";
            }
            AmUi.searchText = q;
        }
        setStatus(AmI18n.t("status_sampling"), 0);
        Thread t = new Thread(() -> {
            AmSnapshot snap = AmSampler.take();
            Application.invokeLater(() -> {
                if (AmUi.alive == 0 || token != AmUi.sampleToken) {
                    return;
                }
                if (snap == null) {
                    setStatus("采样跳过或失败（见日志）", 1);
                    return;
                }
                AmUi.state.applySnapshot(snap);
                applyToTable();
                updateOverview();
                setStatus("已刷新 · " + String.valueOf(AmUi.state.rows.size()) + " 个进程", 0);
            });
        });
        t.start();
    }

    static void updateOverview() {
        if (AmUi.overview == null) {
            return;
        }
        String cpu = AmFormat.cpuPct(AmUi.state.sysCpuMilli);
        String used = AmFormat.bytes(AmUi.state.sysMemUsedBytes);
        String free = AmFormat.bytes(AmUi.state.sysMemFreeBytes);
        String wired = AmFormat.bytes(AmUi.state.sysMemWiredBytes);
        String total = AmFormat.bytes(AmUi.state.memTotal);
        String pause = "";
        if (AmUi.paused != 0) {
            pause = "  |  已暂停";
        }
        AmUi.overview.setText("逻辑核心 " + String.valueOf(AmUi.state.ncpu)
                + "  |  系统 CPU " + cpu
                + "  |  内存合计 " + total
                + "  |  已用(页合计) " + used
                + "  |  空闲页 " + free
                + "  |  Wired " + wired
                + pause);
    }

    static void applyToTable() {
        if (AmUi.table == null || AmUi.state == null) {
            return;
        }
        ArrayList<AmProcRow> filtered = AmUi.state.filtered(AmUi.searchText);
        AmUi.viewRows = sortRows(filtered, AmUi.sortCol, AmUi.sortAsc);
        AmUi.table.setItems(AmUi.viewRows);
        /* restore selection by identity */
        if (AmUi.selPid > 0) {
            int idx = 0;
            int found = -1;
            while (idx < AmUi.viewRows.size()) {
                AmProcRow r = AmUi.viewRows.get(idx);
                if (r.sample.pid == AmUi.selPid
                        && r.sample.startSec == AmUi.selStartSec
                        && r.sample.startUsec == AmUi.selStartUsec) {
                    found = idx;
                    break;
                }
                idx = idx + 1;
            }
            if (found >= 0) {
                AmUi.table.select(found);
                updateDetailLabel(AmUi.viewRows.get(found));
            } else {
                AmUi.detail.setText("所选进程已退出或不再可见（PID " + String.valueOf(AmUi.selPid) + "）");
                AmUi.selPid = -1;
            }
        }
    }

    static ArrayList<AmProcRow> sortRows(ArrayList<AmProcRow> src, int col, int asc) {
        ArrayList<AmProcRow> out = new ArrayList<AmProcRow>();
        int i = 0;
        while (i < src.size()) {
            out.add(src.get(i));
            i = i + 1;
        }
        /* insertion sort */
        int n = out.size();
        int a = 1;
        while (a < n) {
            AmProcRow key = out.get(a);
            String keyS = sortKey(key, col);
            int j = a - 1;
            int moving = 1;
            while (j >= 0 && moving != 0) {
                String js = sortKey(out.get(j), col);
                int cmp = UiStrings.compare(keyS, js);
                int outOfOrder = 0;
                if (asc != 0 && cmp < 0) {
                    outOfOrder = 1;
                }
                if (asc == 0 && cmp > 0) {
                    outOfOrder = 1;
                }
                if (outOfOrder == 0) {
                    moving = 0;
                } else {
                    out.set(j + 1, out.get(j));
                    j = j - 1;
                }
            }
            out.set(j + 1, key);
            a = a + 1;
        }
        return out;
    }

    static String sortKey(AmProcRow r, int col) {
        if (col == 0) {
            String s = String.valueOf(r.sample.pid);
            while (s.length() < 8) {
                s = "0" + s;
            }
            return s;
        }
        if (col == 1) {
            return r.sample.name;
        }
        if (col == 2) {
            return r.sample.user;
        }
        if (col == 3) {
            return AmFormat.cpuSortKey(r.cpuMilli);
        }
        if (col == 4) {
            return AmFormat.bytesSortKey(r.sample.rss);
        }
        if (col == 5) {
            return AmFormat.bytesSortKey(r.sample.footprint);
        }
        if (col == 6) {
            String s = String.valueOf(r.sample.nthreads);
            while (s.length() < 6) {
                s = "0" + s;
            }
            return s;
        }
        return "";
    }

    static void sort(int col) {
        if (AmUi.sortCol == col) {
            if (AmUi.sortAsc != 0) {
                AmUi.sortAsc = 0;
            } else {
                AmUi.sortAsc = 1;
            }
        } else {
            AmUi.sortCol = col;
            AmUi.sortAsc = 0;
        }
        applyToTable();
    }

    static void onSelect(int viewIndex) {
        if (viewIndex < 0 || AmUi.viewRows == null || viewIndex >= AmUi.viewRows.size()) {
            return;
        }
        AmProcRow r = AmUi.viewRows.get(viewIndex);
        AmUi.selPid = r.sample.pid;
        AmUi.selStartSec = r.sample.startSec;
        AmUi.selStartUsec = r.sample.startUsec;
        updateDetailLabel(r);
    }

    static void updateDetailLabel(AmProcRow r) {
        if (AmUi.detail == null || r == null) {
            return;
        }
        String prot = "";
        if (AmProtect.isProtected(r.sample) != 0) {
            prot = "  |  保护：" + AmProtect.reason(r.sample);
        }
        AmUi.detail.setText("PID " + String.valueOf(r.sample.pid)
                + "  PPID " + String.valueOf(r.sample.ppid)
                + "  " + r.sample.name
                + "  用户 " + r.sample.user
                + "  CPU " + AmFormat.cpuPct(r.cpuMilli)
                + "  RSS " + AmFormat.bytes(r.sample.rss)
                + "  物理占用 " + AmFormat.bytes(r.sample.footprint)
                + "  线程 " + AmFormat.threads(r.sample)
                + "  启动 " + AmParse.longToString(r.sample.startSec) + "." + String.valueOf(r.sample.startUsec)
                + prot);
    }

    static void showDetail() {
        AmProcRow r = selectedRow();
        if (r == null) {
            Dialogs.info(AmUi.window, "请先选择一个进程");
            return;
        }
        String msg = "名称：" + r.sample.name + "\n"
                + "PID：" + String.valueOf(r.sample.pid) + "\n"
                + "PPID：" + String.valueOf(r.sample.ppid) + "\n"
                + "用户：" + r.sample.user + " (uid=" + String.valueOf(r.sample.uid) + ")\n"
                + "CPU：" + AmFormat.cpuPct(r.cpuMilli) + "（100%=1 逻辑核心）\n"
                + "RSS：" + AmFormat.bytes(r.sample.rss) + "（pti_resident_size）\n"
                + "物理占用：" + AmFormat.bytes(r.sample.footprint) + "（ri_phys_footprint）\n"
                + "线程数：" + AmFormat.threads(r.sample) + "\n"
                + "启动时间：" + AmParse.longToString(r.sample.startSec) + "." + String.valueOf(r.sample.startUsec) + "\n"
                + "身份键：" + r.sample.identityKey();
        if (AmProtect.isProtected(r.sample) != 0) {
            msg = msg + "\n保护：" + AmProtect.reason(r.sample);
        }
        Dialogs.info(AmUi.window, msg);
    }

    static AmProcRow selectedRow() {
        if (AmUi.selPid <= 0 || AmUi.state == null) {
            return null;
        }
        return AmUi.state.findByIdentity(AmUi.selPid, AmUi.selStartSec, AmUi.selStartUsec);
    }

    /** force=0 SIGTERM, force=1 SIGKILL */
    static void confirmKill(int force) {
        AmProcRow r = selectedRow();
        if (r == null) {
            Dialogs.info(AmUi.window, "请先选择一个进程");
            return;
        }
        if (AmProtect.isProtected(r.sample) != 0) {
            Dialogs.error(AmUi.window, "无法结束：" + AmProtect.reason(r.sample));
            return;
        }
        String mode = "普通终止（SIGTERM）";
        int sig = AmKill.SIGTERM;
        if (force != 0) {
            mode = "强制终止（SIGKILL）";
            sig = AmKill.SIGKILL;
        }
        String msg = "确认对以下进程发送信号？\n\n"
                + "名称：" + r.sample.name + "\n"
                + "PID：" + String.valueOf(r.sample.pid) + "\n"
                + "方式：" + mode + "\n\n"
                + "将先校验启动时间以防 PID 复用。发送成功不等于进程已退出。";
        if (!Dialogs.confirm(AmUi.window, msg)) {
            return;
        }
        /* re-verify identity with fresh fields */
        AmProcSample target = r.sample;
        int code = AmKill.terminate(target, sig);
        if (code != 0) {
            Dialogs.error(AmUi.window, AmKill.describeError(code));
            setStatus(AmKill.describeError(code), 1);
            return;
        }
        setStatus("已发送 " + mode + "，正在确认…", 0);
        int pid = target.pid;
        long ss = target.startSec;
        int su = target.startUsec;
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (Exception e) {
            }
            int still = AmNative.checkIdentity(pid, ss, su);
            Application.invokeLater(() -> {
                if (AmUi.alive == 0) {
                    return;
                }
                if (still == 1) {
                    setStatus("信号已发送，进程仍在运行（可能忽略 SIGTERM）", 0);
                } else {
                    setStatus("进程已退出或身份失效", 0);
                    AmUi.selPid = -1;
                }
                requestSample(1);
            });
        });
        t.start();
    }
}
