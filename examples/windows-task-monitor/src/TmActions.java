/**
 * Navigation, sampling loop, table rebuild, selection, detail window.
 * @author 张晓达
 */
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
        /* Win32 heat via title sniff + optional ##role meta after mvn package. */
        TmUi.table.addColumnDef(colTitle(0, "名称"), new TmColName(), TmTokens.NAME_W, "L", 1, TmTokens.NAME_MAX);
        TmUi.table.addColumnDef(colTitle(1, "状态"), new TmColStatus(), TmTokens.STATUS_W, "L", 0, 0);
        TmUi.table.addColumnDef(colTitle(2, "CPU"), new TmColCpu(), TmTokens.CPU_W, "R", 0, 0);
        TmUi.table.addColumnDef(colTitle(3, "内存"), new TmColMem(), TmTokens.MEM_W, "R", 0, 0);
        TmUi.table.addColumnDef(colTitle(4, "磁盘"), new TmColDisk(), TmTokens.DISK_W, "R", 0, 0);
        TmUi.table.addColumnDef(colTitle(5, "网络"), new TmColNet(), TmTokens.NET_W, "R", 0, 0);
        TmUi.table.syncColumnsNative();
    }

    static void syncNav() {
        if (TmUi.navBtns == null) {
            return;
        }
        int i = 0;
        while (i < TmUi.navBtns.size()) {
            Button b = TmUi.navBtns.get(i);
            Panel ind = TmUi.navInds.get(i);
            int sel = i == TmUi.page ? 1 : 0;
            TmUi.styleNavBtn(b, sel);
            if (sel != 0) {
                ind.setBackgroundRgba(TmUi.accentC);
            } else {
                ind.setBackgroundRgba(TmUi.appBg);
            }
            i = i + 1;
        }
    }

    static String stubMessage(int p) {
        if (p == TmTokens.PAGE_APP_HISTORY) {
            return "应用历史记录 — 未实现\n\n需明确统计对象、累计指标、统计周期与持久化。\n见 docs/windows-task-monitor-w11-parity.md";
        }
        if (p == TmTokens.PAGE_STARTUP) {
            return "启动应用 — 未实现\n\n需枚举真实启动项、启用/禁用与来源。";
        }
        if (p == TmTokens.PAGE_USERS) {
            return "用户 — 未实现\n\n需会话枚举、所属进程与资源汇总。";
        }
        if (p == TmTokens.PAGE_DETAILS) {
            return "详细信息 — 未实现（本轮占位）\n\n后续：平铺进程、可选列、优先级与操作。\n进程详情可在「进程」页通过「更多 → 打开详情」查看。";
        }
        if (p == TmTokens.PAGE_SERVICES) {
            return "服务 — 未实现\n\n需 SCM API：列表、状态、启动/停止与相关进程导航。";
        }
        if (p == TmTokens.PAGE_SETTINGS) {
            return "设置 — 未实现\n\n计划：默认页面、更新速度、主题等。\n当前刷新间隔：进程页「暂停/刷新」；默认 "
                    + String.valueOf(TmUi.refreshMs) + " ms。";
        }
        return "未实现";
    }

    static void showPage(int p) {
        if (p < 0 || p >= TmTokens.PAGE_COUNT) {
            return;
        }
        TmUi.page = p;
        if (TmUi.pageProc != null) {
            TmUi.pageProc.setVisible(p == TmTokens.PAGE_PROC);
        }
        if (TmUi.pagePerf != null) {
            TmUi.pagePerf.setVisible(p == TmTokens.PAGE_PERF);
        }
        if (TmUi.pageStub != null) {
            int stub = 0;
            if (p != TmTokens.PAGE_PROC && p != TmTokens.PAGE_PERF) {
                stub = 1;
            }
            TmUi.pageStub.setVisible(stub != 0);
            if (stub != 0 && TmUi.stubBody != null) {
                TmUi.stubBody.setText(stubMessage(p));
            }
        }
        syncNav();
        if (p == TmTokens.PAGE_PERF) {
            updatePerfPage();
        }
        if (TmUi.window != null && TmUi.window.content != null) {
            TmUi.window.content.layout();
        }
    }

    static void onSearchChanged() {
        if (TmUi.searchField != null) {
            TmUi.searchText = TmUi.searchField.getQuery();
        }
        TmUi.tableDirty = 1;
        maybeRebuildTable(1);
    }

    static void togglePause() {
        if (TmUi.paused != 0) {
            TmUi.paused = 0;
            if (TmUi.pauseBtn != null) {
                TmUi.pauseBtn.setText("暂停");
            }
            requestSample(1);
        } else {
            TmUi.paused = 1;
            if (TmUi.pauseBtn != null) {
                TmUi.pauseBtn.setText("继续");
            }
        }
        updateStatusBar();
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

    static TmViewRow selectedViewRow() {
        if (TmUi.selRowId == null || TmUi.selRowId.length() == 0 || TmUi.viewRows == null) {
            return null;
        }
        int i = 0;
        while (i < TmUi.viewRows.size()) {
            TmViewRow r = TmUi.viewRows.get(i);
            if (r != null && TmUi.selRowId.equals(r.rowId())) {
                return r;
            }
            i = i + 1;
        }
        return null;
    }

    static AmProcRow selectedProcRow() {
        TmViewRow vr = selectedViewRow();
        if (vr == null) {
            return null;
        }
        if (vr.kind == TmViewRow.KIND_PROC || vr.kind == TmViewRow.KIND_GROUP) {
            return vr.proc;
        }
        return null;
    }

    static void onSelectView(TmViewRow row) {
        if (row == null) {
            TmUi.selRowId = "";
            syncCmdEnabled();
            return;
        }
        TmUi.selRowId = row.rowId();
        syncCmdEnabled();
    }

    static void onActivateRow() {
        TmViewRow vr = selectedViewRow();
        if (vr == null) {
            return;
        }
        if (vr.kind == TmViewRow.KIND_SECTION) {
            toggleExpanded("sec:" + vr.sectionId);
            return;
        }
        if (vr.kind == TmViewRow.KIND_GROUP) {
            toggleExpanded("grp:" + vr.groupKey);
            return;
        }
        showDetail();
    }

    static void toggleExpanded(String key) {
        if (TmUi.expanded == null) {
            TmUi.expanded = new HashMap<String, Integer>();
        }
        Integer cur = TmUi.expanded.get(key);
        int next = 0;
        if (cur == null || cur.intValue() != 0) {
            next = 0;
        } else {
            next = 1;
        }
        TmUi.expanded.put(key, next);
        TmUi.tableDirty = 1;
        maybeRebuildTable(1);
    }

    static void syncCmdEnabled() {
        TmViewRow vr = selectedViewRow();
        int canEnd = 0;
        int canEco = 0;
        if (vr != null && vr.kind != TmViewRow.KIND_SECTION) {
            AmProcRow r = vr.proc;
            if (r != null && r.sample != null && AmProtect.isProtected(r.sample) == 0) {
                canEnd = 1;
            }
            if (vr.kind == TmViewRow.KIND_GROUP && vr.members != null) {
                canEnd = 1;
            }
            if (r != null && r.sample != null && r.sample.eco >= 0) {
                canEco = 1;
            }
        }
        if (TmUi.endTaskBtn != null) {
            TmUi.endTaskBtn.setEnabled(canEnd != 0);
            TmUi.styleDangerBtn(TmUi.endTaskBtn, canEnd);
        }
        if (TmUi.ecoBtn != null) {
            TmUi.ecoBtn.setEnabled(canEco != 0);
            TmUi.styleToolBtnState(TmUi.ecoBtn, canEco);
        }
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
        updateDualHeader();
        updatePerfPage();
        if (TmUi.state != null && TmUi.state.sampleCount >= 2) {
            if (forceTable != 0 || TmUi.tableDirty != 0 || TmUi.lastTableRebuildNs == 0) {
                maybeRebuildTable(1);
            }
        }
        syncCmdEnabled();
        refreshDetailIfOpen();
    }

    static void requestSample(int force) {
        if (TmUi.alive == 0) {
            return;
        }
        if (TmUi.samplerBusy != 0 || (AmSampler.inFlight != 0 && force == 0)) {
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
                finishSampleUi(1);
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

    static void updateDualHeader() {
        if (TmUi.state == null) {
            return;
        }
        if (TmUi.dualCpu != null) {
            if (TmUi.state.sysCpuMilli >= 0) {
                TmUi.dualCpu.setText(TmFormat.cpuPct1(TmUi.state.sysCpuMilli));
            } else {
                TmUi.dualCpu.setText(TmFormat.na());
            }
        }
        if (TmUi.dualMem != null) {
            if (TmUi.state.sysMemUsedBytes >= 0) {
                TmUi.dualMem.setText(AmFormat.bytes(TmUi.state.sysMemUsedBytes));
            } else {
                TmUi.dualMem.setText(TmFormat.na());
            }
        }
        if (TmUi.dualDisk != null) {
            TmUi.dualDisk.setText(TmFormat.na());
        }
        if (TmUi.dualNet != null) {
            TmUi.dualNet.setText(TmFormat.na());
        }
    }

    static void updateFooter() {
        if (TmUi.state == null || TmUi.footPrimary == null) {
            return;
        }
        int nproc = 0;
        if (TmUi.state.rows != null) {
            nproc = TmUi.state.rows.size();
        }
        String cpuPart = "";
        if (TmUi.state.sysCpuMilli >= 0) {
            cpuPart = " · CPU " + TmFormat.cpuPct1(TmUi.state.sysCpuMilli);
        }
        TmUi.footPrimary.setText("进程 " + String.valueOf(nproc) + cpuPart
                + " · 磁盘/网络列未采集");
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
            st = "自动刷新 · " + String.valueOf(TmUi.refreshMs) + " ms";
        }
        if (TmUi.lastSampleOk != 0 && TmUi.lastOkMonoNs > 0) {
            long ageMs = (AmNative.monoNs() - TmUi.lastOkMonoNs) / 1000000L;
            if (ageMs < 0) {
                ageMs = 0;
            }
            st = st + " · 上次成功 " + AmParse.longToString(ageMs) + " ms 前";
        } else if (TmUi.state != null && TmUi.state.lastError != null
                && TmUi.state.lastError.length() > 0) {
            st = st + " · " + TmUi.state.lastError;
        }
        TmUi.footStatus.setText(st);
    }

    static void updatePerfPage() {
        if (TmUi.perfCpuLabel == null || TmUi.state == null) {
            return;
        }
        String cpu = TmFormat.na();
        if (TmUi.state.sysCpuMilli >= 0) {
            cpu = TmFormat.cpuPct1(TmUi.state.sysCpuMilli);
        } else if (TmUi.state.sampleCount < 2) {
            cpu = "采样中";
        }
        TmUi.perfCpuLabel.setText("CPU 利用率 " + cpu
                + " · 逻辑处理器 " + String.valueOf(TmUi.state.ncpu));
        if (TmUi.perfMemLabel != null) {
            String mu = TmFormat.na();
            String mt = TmFormat.na();
            if (TmUi.state.sysMemUsedBytes >= 0) {
                mu = AmFormat.bytes(TmUi.state.sysMemUsedBytes);
            }
            if (TmUi.state.memTotal > 0) {
                mt = AmFormat.bytes(TmUi.state.memTotal);
            }
            TmUi.perfMemLabel.setText("内存已用 " + mu + " / 总量 " + mt);
        }
        if (TmUi.cpuChart != null && TmUi.cpuHistory != null) {
            TmUi.cpuChart.setSamplesFromMilli(TmUi.cpuHistory, 0.01);
        }
        if (TmUi.memChart != null && TmUi.memHistory != null) {
            TmUi.memChart.setSamplesFromMilli(TmUi.memHistory, 0.01);
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
        ArrayList<TmViewRow> shown = TmViewModel.build(filtered, TmUi.sortCol, TmUi.sortAsc, TmUi.expanded);
        /* Soft cap — document if truncated */
        int maxShow = 4000;
        if (shown.size() > maxShow) {
            ArrayList<TmViewRow> trim = new ArrayList<TmViewRow>();
            int i = 0;
            while (i < maxShow) {
                trim.add(shown.get(i));
                i = i + 1;
            }
            shown = trim;
        }
        TmUi.viewRows = shown;
        String want = TmUi.selRowId;
        if (want != null && want.length() > 0) {
            TmUi.table.selectedRowId = want;
        }
        int keepTop = -1;
        if (TmUi.table.nativeId > 0) {
            keepTop = UiNative.tableGetScrollY(TmUi.table.nativeId);
        }
        TmUi.table.setItems(TmUi.viewRows);
        if (keepTop >= 0 && TmUi.table.nativeId > 0) {
            UiNative.tableSetScrollY(TmUi.table.nativeId, keepTop);
        }
        if (want != null && want.length() > 0) {
            TmUi.table.selectByRowId(want);
        }
        TmUi.tableDirty = 0;
        TmUi.lastTableRebuildNs = AmNative.monoNs();
        syncCmdEnabled();
    }

    static String detailText(AmProcRow r) {
        if (r == null || r.sample == null) {
            return "在进程页选择一个进程以查看详情。";
        }
        String path = r.sample.path;
        if (path == null || path.length() == 0 || (r.sample.flags & AmFlags.PATH) == 0) {
            path = TmFormat.na();
        }
        String pub = r.sample.publisher;
        if (pub == null || pub.length() == 0) {
            pub = TmFormat.na();
        }
        String mem = TmFormat.bytes(TmViewModel.memOf(r));
        return "名称\t" + r.sample.name + "\n"
                + "PID\t" + String.valueOf(r.sample.pid) + "\n"
                + "PPID\t" + String.valueOf(r.sample.ppid) + "\n"
                + "用户\t" + r.sample.user + "\n"
                + "发布者\t" + pub + "\n"
                + "分组\t" + String.valueOf(r.sample.groupClass)
                + "（0后台/1应用/2Windows）\n"
                + "窗口数\t" + String.valueOf(r.sample.winCount) + "\n"
                + "状态\t" + TmFormat.statusOf(r.sample) + "\n"
                + "效率模式\t" + (r.sample.eco < 0 ? TmFormat.na() : String.valueOf(r.sample.eco)) + "\n"
                + "CPU\t" + TmFormat.cpuPct1(r.cpuMilli) + "\n"
                + "内存\t" + mem + "\n"
                + "磁盘\t" + TmFormat.na() + "（未实现）\n"
                + "网络\t" + TmFormat.na() + "（未实现）\n"
                + "路径\t" + path;
    }

    static void showDetail() {
        AmProcRow r = selectedProcRow();
        if (r == null) {
            Dialogs.info(TmUi.window, "请先选择一个进程");
            return;
        }
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
            box.setBackgroundRgba(TmUi.contentBg);
            TmUi.detailBody = TmUi.mkLabel("", 440, 300, 13, TmUi.textC, TmUi.contentBg);
            Button copy = new Button("复制");
            copy.setPrefSize(72, 32);
            TmUi.styleToolBtn(copy);
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
}
