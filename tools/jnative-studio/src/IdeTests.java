import jnative.ui.*;
import jnative.graphics.*;
import jnative.io.*;

/**
 * Test runner with a result tree. Follows `jnative test`: every Java file under `tests/` (plus files named *Test.java /
 * *Tests.java that have a main method) is a standalone program; exit code 0 means pass. Files are compiled and run one
 * after the other, so the UI stays responsive and the results appear as they come in.
 *
 * @author 张晓达
 */
public class IdeTests {
    IdeApp app;
    ArrayList<String> files = new ArrayList<String>();
    /** 0 pending, 1 running, 2 passed, 3 failed, 4 compile error. */
    ArrayList<int> status = new ArrayList<int>();
    ArrayList<int> millis = new ArrayList<int>();
    ArrayList<String> outputs = new ArrayList<String>();
    int idx = -1;
    long startedAt = 0;
    IdeProc proc;
    boolean running = false;
    boolean stopRequested = false;
    String curOut = "";
    boolean compileFailed = false;

    IdeTests(IdeApp a) {
        app = a;
    }

    // ---------------------------------------------------------------- discovery

    static boolean hasMain(String path) {
        if (!Files.exists(FsPath.of(path))) {
            return false;
        }
        return Files.readString(FsPath.of(path)).indexOf("void main(") >= 0;
    }

    void discover(String dir, ArrayList<String> out, int depth, boolean inTests) {
        if (depth > 8) {
            return;
        }
        ArrayList<String> kids = Files.list(FsPath.of(dir));
        int i = 0;
        while (i < kids.size()) {
            String n = kids.get(i);
            String p = dir + "/" + n;
            i = i + 1;
            if (n.startsWith(".") || n.equals("build") || n.equals("node_modules")) {
                continue;
            }
            if (Files.isDirectory(FsPath.of(p))) {
                discover(p, out, depth + 1, inTests || n.equals("tests"));
            } else if (n.endsWith(".java")) {
                boolean named = n.endsWith("Test.java") || n.endsWith("Tests.java");
                if ((inTests || named) && hasMain(p)) {
                    out.add(p);
                }
            }
        }
    }

    boolean isTestFile(String path) {
        return path.endsWith(".java") && (path.indexOf("/tests/") >= 0 || path.endsWith("Test.java")
                || path.endsWith("Tests.java")) && hasMain(path);
    }

    // ---------------------------------------------------------------- running

    void runAll() {
        if (app.project == null) {
            app.setStatus("请先打开项目");
            return;
        }
        ArrayList<String> list = new ArrayList<String>();
        discover(app.project.dir, list, 0, false);
        if (list.size() == 0) {
            app.showBottomTab(4);
            app.testTree.emptyText = "没有找到测试：在项目的 tests/ 目录下放带 main 的 Java 文件（退出码 0 表示通过），或新建 *Test.java";
            app.testTree.setRoot(new IdeItem("", "", ""));
            app.setStatus("没有找到测试");
            return;
        }
        start(list);
    }

    void runFile(String path) {
        ArrayList<String> list = new ArrayList<String>();
        list.add(path);
        start(list);
    }

    void start(ArrayList<String> list) {
        if (running) {
            app.setStatus("测试正在运行");
            return;
        }
        if (!IdeSettings.hasCompiler()) {
            app.setStatus("未找到编译器");
            return;
        }
        app.saveAll();
        files = list;
        status = new ArrayList<int>();
        millis = new ArrayList<int>();
        outputs = new ArrayList<String>();
        int i = 0;
        while (i < files.size()) {
            status.add(0);
            millis.add(0);
            outputs.add("");
            i = i + 1;
        }
        running = true;
        stopRequested = false;
        idx = -1;
        Files.createDirectories(FsPath.of(app.project.dir + "/.jnative/build/tests"));
        app.showBottomTab(4);
        render();
        next();
    }

    void next() {
        idx = idx + 1;
        if (stopRequested || idx >= files.size()) {
            finish();
            return;
        }
        String src = files.get(idx);
        String name = Ide.baseName(src);
        if (name.endsWith(".java")) {
            name = name.substring(0, name.length() - 5);
        }
        String out = app.project.dir + "/.jnative/build/tests/" + name + "-" + idx;
        status.set(idx, 1);
        curOut = "";
        compileFailed = false;
        startedAt = System.currentTimeMillis();
        render();
        proc = new IdeProc();
        proc.onLine = ln -> gotLine(ln);
        proc.onExit = code -> gotExit(code);
        String cmd = "if ! " + IdeSettings.compileFileCommand(src, out) + "; then echo '@@COMPILE_FAILED'; exit 125; fi; "
                + "JNATIVE_LINEBUF=1 " + IdeState.shq(out) + " 2>&1";
        if (!proc.start(app.project.dir, cmd)) {
            status.set(idx, 4);
            outputs.set(idx, "无法启动编译器");
            next();
        }
    }

    void gotLine(String ln) {
        if (ln.equals("@@COMPILE_FAILED")) {
            compileFailed = true;
            return;
        }
        curOut = curOut.length() == 0 ? ln : curOut + "\n" + ln;
    }

    void gotExit(int code) {
        if (idx < 0 || idx >= files.size()) {
            return;
        }
        millis.set(idx, (int) (System.currentTimeMillis() - startedAt));
        outputs.set(idx, curOut);
        if (compileFailed) {
            status.set(idx, 4);
        } else if (stopRequested && code != 0) {
            status.set(idx, 0);
        } else {
            status.set(idx, code == 0 ? 2 : 3);
            if (code != 0) {
                outputs.set(idx, curOut + (curOut.length() > 0 ? "\n" : "") + "退出码 " + code);
            }
        }
        render();
        next();
    }

    void stop() {
        if (running) {
            stopRequested = true;
            if (proc != null && proc.running()) {
                proc.stop();
            }
        }
    }

    void finish() {
        running = false;
        proc = null;
        render();
        int pass = count(2);
        int bad = count(3) + count(4);
        app.setStatus(stopRequested ? "测试已停止" : "测试完成：" + pass + " 通过，" + bad + " 失败");
        app.refreshState();
    }

    int count(int st) {
        int n = 0;
        int i = 0;
        while (i < status.size()) {
            if (status.get(i) == st) {
                n = n + 1;
            }
            i = i + 1;
        }
        return n;
    }

    // ---------------------------------------------------------------- results tree

    void render() {
        IdeItem root = new IdeItem("", "", "");
        int pass = count(2);
        int bad = count(3) + count(4);
        String sum;
        int col;
        if (running) {
            sum = "运行中 " + Math.min(idx + 1, files.size()) + " / " + files.size() + "　通过 " + pass + "　失败 " + bad;
            col = Ide.ACCENT;
        } else {
            sum = files.size() + " 个测试　通过 " + pass + "　失败 " + bad;
            col = bad > 0 ? Ide.ERROR : Ide.OK;
        }
        IdeItem s = root.add(new IdeItem(sum, "", ""));
        s.color = col;
        int i = 0;
        while (i < files.size()) {
            int st = status.get(i);
            String p = files.get(i);
            String glyph = st == 2 ? "✓ " : (st == 3 ? "✗ " : (st == 4 ? "⚠ " : (st == 1 ? "▶ " : "· ")));
            String label = glyph + Ide.baseName(p);
            if (st >= 2) {
                label = label + "   " + millis.get(i) + " ms";
            }
            IdeItem it = root.add(new IdeItem(label, "cm", p));
            it.hint = app.project != null && p.startsWith(app.project.dir + "/") ? p.substring(app.project.dir.length() + 1) : "";
            it.color = st == 2 ? Ide.OK : (st >= 3 ? Ide.ERROR : (st == 1 ? Ide.ACCENT : Ide.SUBTLE));
            if (st >= 3 && outputs.get(i).length() > 0) {
                it.expanded = true;
                ArrayList<String> ls = DsGen.splitLines(outputs.get(i));
                int k = 0;
                while (k < ls.size() && k < 60) {
                    IdeItem c = it.add(new IdeItem(ls.get(k), "", p));
                    c.color = Ide.TEXT;
                    k = k + 1;
                }
            } else if (st == 2 && outputs.get(i).length() > 0) {
                ArrayList<String> ls = DsGen.splitLines(outputs.get(i));
                int k = 0;
                while (k < ls.size() && k < 60) {
                    IdeItem c = it.add(new IdeItem(ls.get(k), "", p));
                    c.color = Ide.SUBTLE;
                    k = k + 1;
                }
            }
            i = i + 1;
        }
        app.testTree.emptyText = "点击「生成 → 运行全部测试」运行 tests/ 下的测试";
        app.testTree.setRoot(root);
        app.bottomHead.repaint();
    }

    /** Double click: a diagnostic line jumps to its source position, a test row opens the test file. */
    void activate(IdeItem it) {
        if (it.key.length() == 0) {
            return;
        }
        IdeDiag d = IdeDiag.parse(it.text);
        if (d != null && Files.exists(FsPath.of(d.path))) {
            app.openFile(d.path);
            IdeDoc cur = app.activeDoc();
            if (cur != null && cur.kind == 0) {
                cur.editor.goTo(d.line - 1, d.col - 1);
                cur.editor.requestFocus();
            }
            return;
        }
        if (Files.exists(FsPath.of(it.key))) {
            app.navRecord();
            app.openFile(it.key);
        }
    }

    String summary() {
        if (files.size() == 0) {
            return "";
        }
        if (running) {
            return " (运行中)";
        }
        int bad = count(3) + count(4);
        return bad > 0 ? " (" + bad + " 失败)" : " (全部通过)";
    }

    // ---------------------------------------------------------------- templates

    static String testTemplate(String name) {
        StringBuilder sb = new StringBuilder();
        sb.append("/**\n * " + name + "：独立运行的测试程序，退出码 0 表示通过（`jnative test` 同样按此约定）。\n */\n");
        sb.append("public class " + name + " {\n");
        sb.append("    static int failures = 0;\n\n");
        sb.append("    static void check(String what, boolean ok) {\n");
        sb.append("        if (ok) {\n");
        sb.append("            System.out.println(\"ok   \" + what);\n");
        sb.append("        } else {\n");
        sb.append("            failures = failures + 1;\n");
        sb.append("            System.out.println(\"FAIL \" + what);\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
        sb.append("    static void checkEquals(String what, int expected, int actual) {\n");
        sb.append("        if (expected == actual) {\n");
        sb.append("            System.out.println(\"ok   \" + what);\n");
        sb.append("        } else {\n");
        sb.append("            failures = failures + 1;\n");
        sb.append("            System.out.println(\"FAIL \" + what + \": 期望 \" + expected + \"，实际 \" + actual);\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
        sb.append("    public static void main(String[] args) {\n");
        sb.append("        checkEquals(\"1 + 1\", 2, 1 + 1);\n");
        sb.append("        check(\"字符串相等\", \"abc\".equals(\"ab\" + \"c\"));\n");
        sb.append("        if (failures > 0) {\n");
        sb.append("            System.exit(1);\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }
}
