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
        dap.request("launch", "{\"program\":" + IdeLsp.jstr(exe) + ",\"args\":[],\"cwd\":" + IdeLsp.jstr(cwd)
                + ",\"stopOnEntry\":false}", (ok2, b) -> launchedReply(ok2, b));
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
            dap.request("stackTrace", "{\"threadId\":" + threadId + ",\"levels\":40}", (ok, r) -> gotStack(ok, r));
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
    }

    void sendBreakpoints(String path) {
        if (!active || !dap.running()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < app.bpPaths.size()) {
            if (app.bpPaths.get(i).equals(path)) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append("{\"line\":" + (app.bpLines.get(i) + 1) + "}");
            }
            i = i + 1;
        }
        dap.request("setBreakpoints", "{\"source\":{\"path\":" + IdeLsp.jstr(path) + "},\"breakpoints\":[" + sb.toString()
                + "]}", null);
    }

    // ---------------------------------------------------------------- stack and variables

    void gotStack(boolean ok, JsonObject b) {
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
        // the first frame that has a source file we can open is the one to show
        int pick = 0;
        int k = 0;
        while (k < frameNames.size()) {
            if (framePaths.get(k).length() > 0 && Files.exists(FsPath.of(framePaths.get(k)))) {
                pick = k;
                break;
            }
            k = k + 1;
        }
        selectFrame(pick);
        app.debugStopped(stopReason);
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
            return;
        }
        JsonObject sc = b.get("scopes").asArray().get(0).asObject();
        int ref = sc.getInt("variablesReference", 0);
        if (ref <= 0) {
            return;
        }
        dap.request("variables", "{\"variablesReference\":" + ref + "}", (ok2, r) -> gotLocals(ok2, r));
    }

    void gotLocals(boolean ok, JsonObject b) {
        if (!ok) {
            return;
        }
        buildTree(b);
    }

    IdeItem varItem(JsonObject v) {
        String val = v.getString("value", "");
        IdeItem it = new IdeItem(v.getString("name", "") + " = " + val, "cf", "");
        it.hint = v.getString("type", "");
        it.tag = v.getInt("variablesReference", 0);
        if (it.tag > 0) {
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
            parent.add(varItem(va.get(i).asObject()));
            i = i + 1;
        }
    }

    void buildTree(JsonObject locals) {
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
