/**
 * Process-page commands: run new task, end task (group vs single), efficiency, open location.
 * @author 张晓达
 */
class TmCommands {
    static void runNewTask() {
        /* Dialogs.prompt only confirms a suggested string — use it for notepad default;
         * full typed-command UI is PARTIAL vs W11 Run dialog. */
        String cmd = Dialogs.prompt(TmUi.window, "运行新任务（确认将启动建议命令）", "notepad.exe");
        if (cmd == null) {
            return;
        }
        cmd = AmParse.trim(cmd);
        if (cmd.length() == 0) {
            return;
        }
        int code = AmNative.runTask(cmd);
        if (code != 0) {
            Dialogs.error(TmUi.window, "无法启动任务（错误 " + String.valueOf(code) + "）");
            return;
        }
        TmActions.requestSample(1);
    }

    static void openFileLocation() {
        AmProcRow r = TmActions.selectedProcRow();
        if (r == null || r.sample == null) {
            Dialogs.info(TmUi.window, "请先选择一个进程");
            return;
        }
        if ((r.sample.flags & AmFlags.PATH) == 0 || r.sample.path == null || r.sample.path.length() == 0) {
            Dialogs.info(TmUi.window, "无可用路径（权限不足或未解析到映像路径）");
            return;
        }
        int code = AmNative.openFileLocation(r.sample.pid);
        if (code != 0) {
            Dialogs.error(TmUi.window, "无法打开文件位置（错误 " + String.valueOf(code) + "）");
        }
    }

    static void toggleEfficiency() {
        AmProcRow r = TmActions.selectedProcRow();
        if (r == null || r.sample == null) {
            Dialogs.info(TmUi.window, "请先选择一个进程");
            return;
        }
        if (r.sample.eco < 0) {
            Dialogs.info(TmUi.window, "当前系统或进程不支持效率模式（PROCESS_POWER_THROTTLING）");
            return;
        }
        int idOk = AmNative.checkIdentity(r.sample.pid, r.sample.startSec, r.sample.startUsec);
        if (idOk != 1) {
            Dialogs.error(TmUi.window, "进程身份校验失败，未更改效率模式。");
            return;
        }
        int want = r.sample.eco != 0 ? 0 : 1;
        int code = AmNative.setEfficiency(r.sample.pid, want);
        if (code == -1) {
            Dialogs.info(TmUi.window, "效率模式 API 不可用");
            return;
        }
        if (code != 0) {
            Dialogs.error(TmUi.window, "设置效率模式失败（错误 " + String.valueOf(code) + "）");
            return;
        }
        TmActions.requestSample(1);
    }

    static void confirmEndTask() {
        TmViewRow vr = TmActions.selectedViewRow();
        if (vr == null) {
            Dialogs.info(TmUi.window, "请先选择一个进程或应用组");
            return;
        }
        if (vr.kind == TmViewRow.KIND_SECTION) {
            Dialogs.info(TmUi.window, "请选择具体进程或应用组");
            return;
        }
        ArrayList<AmProcSample> targets = new ArrayList<AmProcSample>();
        String label = "";
        if (vr.kind == TmViewRow.KIND_GROUP && vr.members != null) {
            label = "应用组「" + vr.title + "」及其 " + String.valueOf(vr.members.size()) + " 个进程";
            int i = 0;
            while (i < vr.members.size()) {
                AmProcRow r = vr.members.get(i);
                if (r != null && r.sample != null && AmProtect.isProtected(r.sample) == 0) {
                    targets.add(r.sample);
                }
                i = i + 1;
            }
        } else if (vr.proc != null && vr.proc.sample != null) {
            label = "进程 " + vr.proc.sample.name + "（PID " + String.valueOf(vr.proc.sample.pid) + "）";
            if (AmProtect.isProtected(vr.proc.sample) != 0) {
                Dialogs.error(TmUi.window, "无法结束：" + AmProtect.reason(vr.proc.sample));
                return;
            }
            targets.add(vr.proc.sample);
        }
        if (targets.size() == 0) {
            Dialogs.info(TmUi.window, "没有可结束的目标（可能全部受保护）");
            return;
        }
        String msg = "是否结束 " + label + "？\n将对每个目标校验启动时间以防 PID 复用。";
        if (!Dialogs.confirm(TmUi.window, msg)) {
            return;
        }
        int i = 0;
        int fail = 0;
        while (i < targets.size()) {
            AmProcSample t = targets.get(i);
            int idOk = AmNative.checkIdentity(t.pid, t.startSec, t.startUsec);
            if (idOk != 1) {
                fail = fail + 1;
            } else {
                int code = AmKill.terminate(t, AmKill.SIGKILL);
                if (code != 0) {
                    fail = fail + 1;
                }
            }
            i = i + 1;
        }
        if (fail != 0) {
            Dialogs.error(TmUi.window, "部分目标未能结束（" + String.valueOf(fail) + "）");
        }
        TmActions.requestSample(1);
    }

    static void showMoreMenu(int screenX, int screenY) {
        ContextMenu menu = new ContextMenu();
        MenuItem detail = new MenuItem("打开详情", () -> { TmActions.showDetail(); });
        MenuItem loc = new MenuItem("打开文件位置", () -> { openFileLocation(); });
        MenuItem eff = new MenuItem("效率模式", () -> { toggleEfficiency(); });
        MenuItem end = new MenuItem("结束任务", () -> { confirmEndTask(); });
        MenuItem pause = new MenuItem(TmUi.paused != 0 ? "继续更新" : "暂停更新",
                () -> { TmActions.togglePause(); });
        MenuItem refresh = new MenuItem("立即刷新", () -> { TmActions.requestSample(1); });
        menu.add(detail);
        menu.add(loc);
        menu.add(eff);
        menu.addSeparator();
        menu.add(end);
        menu.addSeparator();
        menu.add(pause);
        menu.add(refresh);
        menu.showAt(screenX, screenY);
    }

    static void showRowContextMenu(int screenX, int screenY) {
        showMoreMenu(screenX, screenY);
    }
}
