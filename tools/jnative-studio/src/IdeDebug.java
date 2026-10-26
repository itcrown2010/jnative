import jnative.ui.*;
import jnative.io.*;

/**
 * One debugging session (lldb-dap): launch, breakpoints, stepping, call stack and variables. Results are shown in the
 * "调试" tab of the bottom panel; the stopped line is marked in the editor.
 *
 * @author 张晓达
 */
public class IdeDebug implements IdeDapEvents {
    IdeApp app;
    IdeDap dap = new IdeDap();
    boolean active = false;
    boolean stopped = false;
    boolean launched = false;
    int threadId = 0;
    int curFrame = 0;
    String exe = "";
    String cwd = "";
    String stopReason = "";
    long launchAt = 0;
    boolean sawInitialized = false;
    JsonObject lastLocals = null;
    ArrayList<String> watchResults = new ArrayList<String>();
    int autoSkips = 0;
    String lastFn = "";
    int lastLine = -1;
    ArrayList<String> frameNames = new ArrayList<String>();
    ArrayList<String> framePaths = new ArrayList<String>();
    ArrayList<Integer> frameLines = new ArrayList<Integer>();
    ArrayList<Integer> frameIds = new ArrayList<Integer>();

    IdeDebug(IdeApp a) {
        app = a;
    }

    // ---------------------------------------------------------------- lifecycle

    /** Starts lldb-dap and launches the (debug-built) program. */
    boolean launch(String exePath, String workDir) {
        String adapter = IdeDap.findAdapter();
        if (adapter.length() == 0) {
            app.appendOutput("找不到 lldb-dap：请安装 Xcode 或 Command Line Tools（xcode-select --install）", Ide.ERROR);
            return false;
        }
        exe = exePath;
        cwd = workDir;
        if (!dap.start(adapter, this)) {
            app.appendOutput("无法启动 lldb-dap", Ide.ERROR);
            return false;
        }
        active = true;
        stopped = false;
        launched = false;
        clearState();
        app.debugTree.setRoot(new IdeItem("", "", ""));
        app.debugTree.emptyText = "正在启动调试器…";
        launchAt = System.currentTimeMillis();
        sawInitialized = false;
        autoSkips = 0;
        lastFn = "";
        lastLine = -1;
        app.appendOutput("正在启动 lldb-dap…", Ide.ACCENT);
        dap.request("initialize", "{\"adapterID\":\"jnative\",\"clientID\":\"jnative-studio\",\"linesStartAt1\":true,"
                + "\"columnsStartAt1\":true,\"pathFormat\":\"path\"}", (ok, b) -> initialized(ok));
        return true;
    }

    void initialized(boolean ok) {
        if (!ok) {
            app.appendOutput("调试器初始化失败 " + dap.errTail, Ide.ERROR);
            end();
            return;
        }
        app.appendOutput("lldb-dap 已连接，启动 " + Ide.baseName(exe), Ide.OK);
        String init = "";
        String fmt = IdeSettings.lldbFormatters();
        if (fmt.length() > 0) {
            // String / array / object formatters: readable values instead of raw pointers
            init = ",\"initCommands\":[" + IdeLsp.jstr("command script import '" + fmt + "'") + "]";
        }
        dap.request("launch", "{\"program\":" + IdeLsp.jstr(exe) + ",\"args\":" + app.state.jsonArgs()
                + ",\"env\":" + app.state.jsonEnv() + ",\"cwd\":" + IdeLsp.jstr(cwd)
                + ",\"stopOnEntry\":false" + init + "}", (ok2, b) -> launchedReply(ok2, b));
    }

    void launchedReply(boolean ok, JsonObject b) {
        if (!ok) {
            app.appendOutput("无法启动被调试程序：" + exe + " " + dap.errTail, Ide.ERROR);
            end();
            return;
        }
        launched = true;
        app.appendOutput("程序已交给调试器", Ide.OK);
    }

    void end() {
        boolean was = active;
        active = false;
        stopped = false;
        launched = false;
        launchAt = 0;
        sawInitialized = false;
        app.tempBpPath = "";
        app.tempBpLine = -1;
        lastLocals = null;
        dap.stop();
        clearState();
        app.debugEnded(was);
    }

    /** Stops the session (kills the debuggee). */
    void stop() {
        if (!active) {
            return;
        }
        if (dap.running()) {
            dap.request("disconnect", "{\"terminateDebuggee\":true}", null);
        }
        end();
    }

    void clearState() {
        frameNames = new ArrayList<String>();
        framePaths = new ArrayList<String>();
        frameLines = new ArrayList<Integer>();
        frameIds = new ArrayList<Integer>();
        curFrame = 0;
        app.clearExecLine();
    }

    // ---------------------------------------------------------------- events

    public void event(String name, JsonObject b) {
        if (name.equals("initialized")) {
            sawInitialized = true;
            sendAllBreakpoints();
            dap.request("setExceptionBreakpoints", "{\"filters\":[]}", (ok, r) -> configurationReady());
        } else if (name.equals("stopped")) {
            threadId = b.getInt("threadId", threadId);
            stopReason = b.getString("reason", "");
            stopped = true;
            boolean entry = stopReason.equals("entry") || stopReason.equals("process spawned")
                    || stopReason.equals("started");
            dap.request("stackTrace", "{\"threadId\":" + threadId + ",\"levels\":40}",
                    (ok, r) -> gotStack(ok, r, entry));
        } else if (name.equals("continued")) {
            stopped = false;
            app.clearExecLine();
            app.debugRunning();
        } else if (name.equals("output")) {
            String cat = b.getString("category", "console");
            String text = b.getString("output", "");
            if (cat.equals("stdout") || cat.equals("stderr")) {
                app.appendProgramOutput(text, cat.equals("stderr"));
            }
        } else if (name.equals("terminated")) {
            if (active) {
                app.appendOutput("—— 调试结束 ——", Ide.WARN);
                end();
            }
        } else if (name.equals("exited")) {
            if (!stopped && frameNames.size() == 0) {
                app.appendOutput("程序已结束，没有在源码处暂停。请确认用「调试」启动，不要用「运行」。", Ide.WARN);
            }
            app.appendOutput("—— 程序已退出（代码 " + b.getInt("exitCode", 0) + "）——",
                    b.getInt("exitCode", 0) == 0 ? Ide.OK : Ide.ERROR);
        }
    }

    public void closed() {
        if (active) {
            app.appendOutput("—— 调试器已断开 —— " + dap.errTail, Ide.WARN);
            end();
        }
    }

    // ---------------------------------------------------------------- breakpoints

    void configurationReady() {
        dap.request("configurationDone", "{}", (ok, r) -> app.appendOutput("—— 调试开始 ——", Ide.ACCENT));
    }

    void sendAllBreakpoints() {
        ArrayList<String> done = new ArrayList<String>();
        int i = 0;
        while (i < app.bpPaths.size()) {
            String p = app.bpPaths.get(i);
            if (done.indexOf(p) < 0) {
                done.add(p);
                sendBreakpoints(p);
            }
            i = i + 1;
        }
        if (app.tempBpPath.length() > 0 && done.indexOf(app.tempBpPath) < 0) {
            sendBreakpoints(app.tempBpPath);
        }
    }

    void sendBreakpoints(String path) {
        if (!active || !dap.running()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        boolean tempDone = false;
        while (i < app.bpPaths.size()) {
            if (app.bpPaths.get(i).equals(path)) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                int ln = app.bpLines.get(i);
                String cond = app.bpConds.get(i);
                if (cond.length() > 0) {
                    sb.append("{\"line\":" + (ln + 1) + ",\"condition\":" + IdeLsp.jstr(cond) + "}");
                } else {
                    sb.append("{\"line\":" + (ln + 1) + "}");
                }
                if (app.tempBpLine == ln && app.tempBpPath.equals(path) && cond.length() == 0) {
                    tempDone = true;
                }
            }
            i = i + 1;
        }
        if (app.tempBpLine >= 0 && app.tempBpPath.equals(path) && !tempDone) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append("{\"line\":" + (app.tempBpLine + 1) + "}");
        }
        dap.request("setBreakpoints", "{\"source\":{\"path\":" + IdeLsp.jstr(path) + "},\"breakpoints\":[" + sb.toString()
                + "]}", null);
    }

    // ---------------------------------------------------------------- stack and variables

    void gotStack(boolean ok, JsonObject b, boolean entryStop) {
        clearState();
        if (!ok) {
            return;
        }
        JsonValue fv = b.get("stackFrames");
        if (fv.isArray()) {
            JsonArray fa = fv.asArray();
            int i = 0;
            while (i < fa.size()) {
                JsonObject f = fa.get(i).asObject();
                frameIds.add(f.getInt("id", 0));
                frameNames.add(f.getString("name", "?"));
                frameLines.add(f.getInt("line", 0));
                String path = "";
                JsonValue sv = f.get("source");
                if (sv.isObject()) {
                    path = sv.asObject().getString("path", "");
                }
                framePaths.add(path);
                i = i + 1;
            }
        }
        int pick = pickUserFrame();
        int line0 = -1;
        if (frameNames.size() > 0) {
            line0 = frameLines.get(0);
        }
        boolean sameSpot = frameNames.size() > 0 && stopReason.equals("step")
                && frameNames.get(0).equals(lastFn) && line0 == lastLine;
        if (frameNames.size() > 0) {
            lastFn = frameNames.get(0);
            lastLine = line0;
        }
        if (!entryStop && (stackLooksBroken() || sameSpot) && autoSkips < 3) {
            // the step landed in the function epilogue (frame already popped): locals would be garbage
            autoSkips = autoSkips + 1;
            stopped = false;
            dap.request("next", "{\"threadId\":" + threadId + "}", null);
            return;
        }
        autoSkips = 0;
        if (!entryStop && pick < 0 && stopReason.equals("step")) {
            app.appendOutput("已单步出入口函数，继续运行至结束", Ide.ACCENT);
            resume();
            return;
        }
        if (entryStop && pick < 0) {
            app.appendOutput("已越过加载器入口，继续跑到断点…", Ide.ACCENT);
            resume();
            return;
        }
        if (pick < 0) {
            pick = 0;
        }
        selectFrame(pick);
        app.debugStopped(stopReason);
        if (app.tempBpLine >= 0 && pick < frameNames.size() && framePaths.get(pick).equals(app.tempBpPath)
                && frameLines.get(pick) - 1 == app.tempBpLine) {
            // run-to-cursor arrived: drop the one-shot breakpoint
            String tp = app.tempBpPath;
            app.tempBpPath = "";
            app.tempBpLine = -1;
            sendBreakpoints(tp);
        }
    }

    /** True when the caller frame cannot be unwound (name is a bare address) — typical for the ret instruction. */
    boolean stackLooksBroken() {
        if (frameNames.size() == 0) {
            return false;
        }
        if (frameNames.size() == 1) {
            return isUserSource(framePaths.get(0));
        }
        String n = frameNames.get(1);
        return n.length() == 0 || n.startsWith("0x") || n.equals("?");
    }

    /** Index of the first Java / JNative frame; -1 when the stack is only the loader or C runtime. */
    int pickUserFrame() {
        int k = 0;
        while (k < frameNames.size()) {
            if (isUserSource(framePaths.get(k))) {
                return k;
            }
            k = k + 1;
        }
        return -1;
    }

    static boolean isUserSource(String path) {
        if (path == null || path.length() == 0) {
            return false;
        }
        String low = path.toLowerCase();
        if (low.contains("/usr/lib/") || low.contains("/usr/lib/system/") || low.contains("/system/library/")) {
            return false;
        }
        return low.endsWith(".java") || low.endsWith(".jn");
    }

    static boolean isTempVar(String name) {
        return name.startsWith("gctmp") || name.startsWith("__") || name.equals("this.addr");
    }

    void selectFrame(int idx) {
        if (idx < 0 || idx >= frameIds.size()) {
            return;
        }
        curFrame = idx;
        String path = framePaths.get(idx);
        if (path.length() > 0 && Files.exists(FsPath.of(path))) {
            app.showExecLine(path, frameLines.get(idx) - 1);
        }
        dap.request("scopes", "{\"frameId\":" + frameIds.get(idx) + "}", (ok, r) -> gotScopes(ok, r));
        buildTree(null);
    }

    void gotScopes(boolean ok, JsonObject b) {
        if (!ok || !b.get("scopes").isArray() || b.get("scopes").asArray().size() == 0) {
            buildTree(new JsonObject());
            return;
        }
        JsonArray scopes = b.get("scopes").asArray();
        int ref = 0;
        int i = 0;
        while (i < scopes.size()) {
            JsonObject sc = scopes.get(i).asObject();
            String nm = sc.getString("name", "").toLowerCase();
            int r = sc.getInt("variablesReference", 0);
            if (r > 0 && (nm.equals("locals") || nm.equals("local") || nm.equals("arguments")
                    || nm.equals("args") || nm.contains("局部"))) {
                ref = r;
                break;
            }
            i = i + 1;
        }
        if (ref <= 0) {
            i = 0;
            while (i < scopes.size()) {
                int r = scopes.get(i).asObject().getInt("variablesReference", 0);
                String nm = scopes.get(i).asObject().getString("name", "").toLowerCase();
                if (r > 0 && !nm.contains("register") && !nm.contains("寄存器")) {
                    ref = r;
                    break;
                }
                i = i + 1;
            }
        }
        if (ref <= 0) {
            buildTree(new JsonObject());
            return;
        }
        dap.request("variables", "{\"variablesReference\":" + ref + "}", (ok2, r) -> gotLocals(ok2, r));
    }

    void gotLocals(boolean ok, JsonObject b) {
        buildTree(ok ? b : new JsonObject());
        refreshWatches();
    }

    IdeItem varItem(JsonObject v) {
        String val = v.getString("value", "");
        IdeItem it = new IdeItem(v.getString("name", "") + " = " + val, "cf", "");
        it.hint = v.getString("type", "");
        it.tag = v.getInt("variablesReference", 0);
        String vt = v.getString("type", "");
        boolean formatted = val.indexOf("{") >= 0 || val.startsWith("\"") || val.indexOf(" \"") >= 0;
        boolean strOrArr = vt.equals("String *") || vt.endsWith("[] *");
        if (it.tag > 0 && !(strOrArr && formatted)) {
            it.add(new IdeItem("…", "", ""));
        }
        return it;
    }

    void fillVars(IdeItem parent, JsonObject b) {
        JsonValue vv = b.get("variables");
        if (!vv.isArray()) {
            return;
        }
        JsonArray va = vv.asArray();
        int i = 0;
        while (i < va.size()) {
            JsonObject vo = va.get(i).asObject();
            if (!isTempVar(vo.getString("name", ""))) {
                parent.add(varItem(vo));
            }
            i = i + 1;
        }
    }

    // ---------------------------------------------------------------- watches and evaluation

    /** Re-evaluates every watch expression in the selected frame (call after a stop or frame change). */
    void refreshWatches() {
        watchResults = new ArrayList<String>();
        int n = app.state.watches.size();
        int i = 0;
        while (i < n) {
            watchResults.add(active && stopped ? "…" : "（未暂停）");
            i = i + 1;
        }
        if (active && stopped && curFrame < frameIds.size()) {
            i = 0;
            while (i < n) {
                watchOne(i, app.state.watches.get(i));
                i = i + 1;
            }
        }
        if (active) {
            buildTree(lastLocals);
        }
    }

    void watchOne(int idx, String expr) {
        dap.request("evaluate", "{\"expression\":" + IdeLsp.jstr(expr) + ",\"frameId\":" + frameIds.get(curFrame)
                + ",\"context\":\"watch\"}", (ok, r) -> gotWatch(idx, expr, ok, r));
    }

    void gotWatch(int idx, String expr, boolean ok, JsonObject r) {
        if (idx >= watchResults.size() || idx >= app.state.watches.size() || !app.state.watches.get(idx).equals(expr)) {
            return;
        }
        if (ok) {
            watchResults.set(idx, r.getString("result", ""));
        } else {
            watchResults.set(idx, "<无法求值>");
        }
        buildTree(lastLocals);
    }

    /** One-off evaluation in the selected frame; the result goes to the Output panel. */
    void evaluate(String expr) {
        if (!active || !stopped || curFrame >= frameIds.size()) {
            return;
        }
        app.showBottomTab(0);
        dap.request("evaluate", "{\"expression\":" + IdeLsp.jstr(expr) + ",\"frameId\":" + frameIds.get(curFrame)
                + ",\"context\":\"repl\"}", (ok, r) -> {
            if (ok) {
                app.appendOutput(expr + " = " + r.getString("result", ""), Ide.OK);
            } else {
                String m = r.getString("message", "");
                app.appendOutput(expr + " → 无法求值" + (m.length() > 0 ? "：" + m : ""), Ide.ERROR);
            }
        });
    }

    void buildTree(JsonObject locals) {
        lastLocals = locals;
        IdeItem root = new IdeItem("", "", "");
        IdeItem st = root.add(new IdeItem("调用栈", "", ""));
        st.expanded = true;
        int i = 0;
        while (i < frameNames.size()) {
            String where = framePaths.get(i).length() > 0 ? Ide.baseName(framePaths.get(i)) + ":" + frameLines.get(i) : "";
            IdeItem f = st.add(new IdeItem((i == curFrame ? "▶ " : "") + frameNames.get(i), "cm", "" + i));
            f.hint = where;
            f.tag = i;
            i = i + 1;
        }
        IdeItem vr = root.add(new IdeItem("变量", "", ""));
        vr.expanded = true;
        if (locals != null) {
            fillVars(vr, locals);
            if (vr.kids.size() == 0) {
                vr.add(new IdeItem("（无局部变量）", "", ""));
            }
        } else {
            vr.add(new IdeItem("加载中…", "", ""));
        }
        if (app.state.watches.size() > 0) {
            IdeItem wr = root.add(new IdeItem("监视", "", ""));
            wr.expanded = true;
            int w = 0;
            while (w < app.state.watches.size()) {
                String ex = app.state.watches.get(w);
                String val = w < watchResults.size() ? watchResults.get(w) : "…";
                IdeItem wi = wr.add(new IdeItem(ex + " = " + val, "cf", ex));
                wi.hint = "双击删除";
                w = w + 1;
            }
        }
        app.debugTree.setRoot(root);
    }

    /** Lazily loads the children of a structured variable when its row is expanded. */
    void expand(IdeItem it) {
        if (it.tag <= 0 || it.kids.size() != 1 || !it.kids.get(0).text.equals("…") || !stopped) {
            return;
        }
        dap.request("variables", "{\"variablesReference\":" + it.tag + "}", (ok, r) -> expanded(it, ok, r));
    }

    void expanded(IdeItem it, boolean ok, JsonObject r) {
        if (!ok) {
            return;
        }
        it.kids.clear();
        fillVars(it, r);
        app.debugTree.rebuild();
    }

    // ---------------------------------------------------------------- stepping

    void resume() {
        if (active && stopped) {
            dap.request("continue", "{\"threadId\":" + threadId + "}", null);
        }
    }

    void stepOver() {
        if (active && stopped) {
            dap.request("next", "{\"threadId\":" + threadId + "}", null);
        }
    }

    void stepIn() {
        if (active && stopped) {
            dap.request("stepIn", "{\"threadId\":" + threadId + "}", null);
        }
    }

    void stepOut() {
        if (active && stopped) {
            dap.request("stepOut", "{\"threadId\":" + threadId + "}", null);
        }
    }

    void pause() {
        if (active && !stopped) {
            dap.request("pause", "{\"threadId\":" + threadId + "}", null);
        }
    }
}
