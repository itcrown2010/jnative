import jnative.ui.*;
import jnative.graphics.*;
import jnative.io.*;

/**
 * Git integration through the `git` command line: branch + change count in the status bar, colored explorer entries
 * (modified / added / untracked / deleted), a changed-files list, the diff of the current file, commit, revert, log,
 * pull and push. Everything runs as asynchronous child processes (see IdeProc); there is no UI blocking.
 *
 * @author 张晓达
 */
public class IdeGit {
    static int C_MODIFIED = 0xC9822B;
    static int C_ADDED = 0x2E9D4F;
    static int C_DELETED = 0xD13438;
    static int C_RENAMED = 0x3B82C4;
    static int C_CONFLICT = 0xD13438;

    IdeApp app;
    boolean repo = false;
    String branch = "";
    String tracking = "";
    /** Paths relative to the project directory ("dir/" entries mean a whole untracked folder). */
    ArrayList<String> paths = new ArrayList<String>();
    ArrayList<String> codes = new ArrayList<String>();
    IdeProc statusProc;
    IdeProc cmdProc;
    boolean refreshAgain = false;
    String statusRoot = "";
    String statusPrefix = "";
    ArrayList<String> statusLines = new ArrayList<String>();

    IdeGit(IdeApp a) {
        app = a;
    }

    void reset() {
        repo = false;
        branch = "";
        tracking = "";
        paths = new ArrayList<String>();
        codes = new ArrayList<String>();
    }

    int changeCount() {
        return paths.size();
    }

    // ---------------------------------------------------------------- gutter (changes since the last commit)

    /** Reads the HEAD version of a document's file (asynchronously); the gutter marks follow in {@link IdeApp#gutterTick}. */
    void loadBase(IdeDoc d) {
        String rel = "";
        if (repo && app.project != null && d.path.startsWith(app.project.dir + "/")) {
            rel = d.path.substring(app.project.dir.length() + 1);
        }
        if (rel.length() == 0 || rel.indexOf("'") >= 0) {
            d.baseState = 3;
            return;
        }
        d.baseState = 1;
        ArrayList<String> got = new ArrayList<String>();
        IdeProc pr = new IdeProc();
        d.baseProc = pr;
        pr.onLine = ln -> got.add(ln.endsWith("\r") ? ln.substring(0, ln.length() - 1) : ln);
        pr.onExit = code -> {
            if (d.baseProc != pr) {
                return;
            }
            if (code == 0) {
                d.baseLines = got;
                d.baseState = 2;
            } else {
                d.baseState = 3;
            }
            d.diffVersion = -1;
        };
        if (!pr.start(app.project.dir, "GIT_OPTIONAL_LOCKS=0 git show 'HEAD:./" + rel + "' 2>/dev/null")) {
            d.baseState = 3;
        }
    }

    /** Forces every open document to re-read its committed text (after a commit, revert, pull...). */
    void invalidateBases() {
        int i = 0;
        while (i < app.tabs.docs.size()) {
            IdeDoc d = app.tabs.docs.get(i);
            if (d.kind == 0 && d.baseState != 1) {
                d.baseState = 0;
            }
            i = i + 1;
        }
    }

    // ---------------------------------------------------------------- status

    void refresh() {
        if (app.project == null) {
            reset();
            return;
        }
        if (statusProc != null && statusProc.running()) {
            refreshAgain = true;
            return;
        }
        statusLines = new ArrayList<String>();
        statusProc = new IdeProc();
        statusProc.onLine = ln -> statusLines.add(ln);
        statusProc.onExit = code -> gotStatus(code);
        boolean ok = statusProc.start(app.project.dir,
                "GIT_OPTIONAL_LOCKS=0 git rev-parse --show-prefix 2>/dev/null && "
                + "GIT_OPTIONAL_LOCKS=0 git -c core.quotepath=false status --porcelain=v1 -b 2>/dev/null");
        if (!ok) {
            reset();
        }
    }

    void gotStatus(int code) {
        boolean again = refreshAgain;
        refreshAgain = false;
        reset();
        if (code == 0 && statusLines.size() > 0) {
            repo = true;
            String prefix = statusLines.get(0);
            int i = 1;
            while (i < statusLines.size()) {
                String ln = statusLines.get(i);
                i = i + 1;
                if (ln.startsWith("## ")) {
                    parseBranch(ln.substring(3));
                } else if (ln.length() > 3) {
                    String xy = ln.substring(0, 2);
                    String p = ln.substring(3);
                    int arrow = p.indexOf(" -> ");
                    if (arrow >= 0) {
                        p = p.substring(arrow + 4);
                    }
                    if (p.startsWith("\"") && p.endsWith("\"") && p.length() >= 2) {
                        p = p.substring(1, p.length() - 1);
                    }
                    if (prefix.length() > 0) {
                        if (!p.startsWith(prefix)) {
                            continue;
                        }
                        p = p.substring(prefix.length());
                    }
                    paths.add(p);
                    codes.add(xy);
                }
            }
        }
        decorate(app.explorer.root);
        app.explorer.repaint();
        app.status.repaint();
        invalidateBases();
        if (again) {
            refresh();
        }
    }

    void parseBranch(String s) {
        String b = s;
        int sp = b.indexOf(" [");
        String ahead = "";
        if (sp >= 0) {
            ahead = b.substring(sp + 2, b.length() - 1);
            b = b.substring(0, sp);
        }
        int dots = b.indexOf("...");
        if (dots >= 0) {
            tracking = b.substring(dots + 3);
            b = b.substring(0, dots);
        }
        if (b.startsWith("No commits yet on ")) {
            b = b.substring(18);
        }
        branch = b;
        if (ahead.length() > 0) {
            tracking = tracking + " " + ahead;
        }
    }

    /** Status-bar text, e.g. "⎇ main  ●3" (empty outside a repository). */
    String statusText() {
        if (!repo) {
            return "";
        }
        String t = "⎇ " + branch;
        if (paths.size() > 0) {
            t = t + "  ●" + paths.size();
        }
        return t;
    }

    static int colorOf(String xy) {
        if (xy.equals("??") || xy.charAt(0) == 'A' || xy.charAt(1) == 'A') {
            return C_ADDED;
        }
        if (xy.charAt(0) == 'U' || xy.charAt(1) == 'U' || xy.equals("DD")) {
            return C_CONFLICT;
        }
        if (xy.charAt(0) == 'D' || xy.charAt(1) == 'D') {
            return C_DELETED;
        }
        if (xy.charAt(0) == 'R') {
            return C_RENAMED;
        }
        return C_MODIFIED;
    }

    static String label(String xy) {
        if (xy.equals("??")) {
            return "未跟踪";
        }
        if (xy.charAt(0) == 'U' || xy.charAt(1) == 'U') {
            return "冲突";
        }
        if (xy.charAt(0) == 'A') {
            return "已添加";
        }
        if (xy.charAt(0) == 'D' || xy.charAt(1) == 'D') {
            return "已删除";
        }
        if (xy.charAt(0) == 'R') {
            return "已重命名";
        }
        return "已修改";
    }

    /** Colors explorer entries in place; returns true when the subtree has any change. */
    boolean decorate(IdeItem node) {
        boolean any = false;
        int i = 0;
        while (i < node.kids.size()) {
            IdeItem c = node.kids.get(i);
            i = i + 1;
            boolean isDir = c.icon.equals("folder") || c.icon.equals("project");
            c.color = -1;
            if (!repo || app.project == null || c.key.length() <= app.project.dir.length()) {
                if (isDir) {
                    decorate(c);
                }
                continue;
            }
            String rel = c.key.substring(app.project.dir.length() + 1);
            if (isDir) {
                if (decorate(c)) {
                    c.color = C_MODIFIED;
                    any = true;
                } else {
                    int k = indexOfPath(rel + "/");
                    if (k >= 0) {
                        c.color = colorOf(codes.get(k));
                        any = true;
                    }
                }
            } else {
                int k = indexOfPath(rel);
                if (k < 0) {
                    k = coveredByUntrackedDir(rel);
                }
                if (k >= 0) {
                    c.color = colorOf(codes.get(k));
                    any = true;
                }
            }
        }
        return any;
    }

    int indexOfPath(String rel) {
        int i = 0;
        while (i < paths.size()) {
            if (paths.get(i).equals(rel)) {
                return i;
            }
            i = i + 1;
        }
        return -1;
    }

    int coveredByUntrackedDir(String rel) {
        int i = 0;
        while (i < paths.size()) {
            String p = paths.get(i);
            if (p.endsWith("/") && rel.startsWith(p)) {
                return i;
            }
            i = i + 1;
        }
        return -1;
    }

    // ---------------------------------------------------------------- commands

    boolean available() {
        if (app.project == null) {
            app.setStatus("请先打开项目");
            return false;
        }
        if (!repo) {
            app.setStatus("当前项目不在 Git 仓库中（先在项目目录执行 git init）");
            return false;
        }
        return true;
    }

    /** Runs a git command and streams its output into the Output panel; afterwards refreshes the status. */
    void run(String title, String command, boolean colorDiff) {
        if (cmdProc != null && cmdProc.running()) {
            app.setStatus("上一个 Git 命令还在运行");
            return;
        }
        app.showBottomTab(0);
        app.appendOutput("$ " + title, Ide.SUBTLE);
        cmdProc = new IdeProc();
        cmdProc.onLine = ln -> app.appendOutput(ln, colorDiff ? diffColor(ln) : Ide.TEXT);
        cmdProc.onExit = code -> {
            if (code != 0) {
                app.appendOutput("git 退出码 " + code, Ide.ERROR);
            }
            app.setStatus(code == 0 ? title + " 完成" : title + " 失败");
            app.reloadAfterGit();
            refresh();
        };
        if (!cmdProc.start(app.project.dir, "GIT_TERMINAL_PROMPT=0 " + command + " 2>&1")) {
            app.setStatus("无法启动 git");
        }
    }

    static int diffColor(String ln) {
        if (ln.startsWith("+++") || ln.startsWith("---") || ln.startsWith("diff ")) {
            return Ide.SUBTLE;
        }
        if (ln.startsWith("@@")) {
            return C_RENAMED;
        }
        if (ln.startsWith("+")) {
            return C_ADDED;
        }
        if (ln.startsWith("-")) {
            return C_DELETED;
        }
        return Ide.TEXT;
    }

    String relPath(String abs) {
        if (app.project != null && abs.startsWith(app.project.dir + "/")) {
            return abs.substring(app.project.dir.length() + 1);
        }
        return abs;
    }

    void showChanges() {
        if (!available()) {
            return;
        }
        ArrayList<IdeItem> items = new ArrayList<IdeItem>();
        int i = 0;
        while (i < paths.size()) {
            String p = paths.get(i);
            String xy = codes.get(i);
            String name = p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
            int sl = name.lastIndexOf("/");
            IdeItem it = new IdeItem(sl >= 0 ? name.substring(sl + 1) : name,
                    p.endsWith("/") ? "folder" : Ide.iconForFile(name), app.project.dir + "/" + name);
            it.hint = label(xy) + " · " + p;
            it.color = colorOf(xy);
            items.add(it);
            i = i + 1;
        }
        if (items.size() == 0) {
            app.setStatus("工作区干净，没有更改");
        }
        app.quick.open("更改的文件（" + items.size() + "）· 分支 " + branch, items, it -> {
            if (!Files.isDirectory(FsPath.of(it.key)) && Files.exists(FsPath.of(it.key))) {
                app.navRecord();
                app.openFile(it.key);
            }
        });
    }

    void diffCurrent() {
        if (!available()) {
            return;
        }
        IdeDoc d = app.activeDoc();
        if (d == null || d.path.length() == 0) {
            run("git diff", "git --no-pager diff --no-color", true);
            return;
        }
        String rel = relPath(d.path);
        int k = indexOfPath(rel);
        if (k >= 0 && codes.get(k).equals("??")) {
            app.setStatus(Ide.baseName(d.path) + " 是未跟踪的新文件，没有可比较的版本");
            return;
        }
        run("git diff " + rel, "git --no-pager diff --no-color HEAD -- " + IdeState.shq(rel), true);
    }

    void diffAll() {
        if (!available()) {
            return;
        }
        run("git diff", "git --no-pager diff --no-color HEAD", true);
    }

    void log() {
        if (!available()) {
            return;
        }
        run("git log", "git --no-pager log --oneline --decorate -n 40", false);
    }

    void commit() {
        if (!available()) {
            return;
        }
        app.saveAll();
        if (paths.size() == 0) {
            app.setStatus("没有可提交的更改");
            return;
        }
        IdeDialog.input(app.window, "提交", "提交说明（将提交项目目录下全部 " + paths.size() + " 项更改）：", "", msg -> {
            run("git commit", "git add -A -- . && git commit -m " + IdeState.shq(msg), false);
        });
    }

    void revertCurrent() {
        if (!available()) {
            return;
        }
        IdeDoc d = app.activeDoc();
        if (d == null || d.path.length() == 0) {
            return;
        }
        String rel = relPath(d.path);
        int k = indexOfPath(rel);
        if (k < 0) {
            app.setStatus(Ide.baseName(d.path) + " 没有更改");
            return;
        }
        if (codes.get(k).equals("??")) {
            app.setStatus("未跟踪文件无法还原（请直接删除）");
            return;
        }
        if (!Dialogs.confirm(app.window, "放弃 " + Ide.baseName(d.path) + " 的全部未提交更改并恢复到最近一次提交？此操作不可撤销。")) {
            return;
        }
        run("git checkout " + rel, "git checkout HEAD -- " + IdeState.shq(rel), false);
    }

    void pull() {
        if (!available()) {
            return;
        }
        run("git pull", "git pull --ff-only", false);
    }

    void push() {
        if (!available()) {
            return;
        }
        run("git push", "git push", false);
    }
}
