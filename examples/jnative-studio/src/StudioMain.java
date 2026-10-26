/**
 * JNative Studio Core — CLI MVP (Phase 5).
 * GUI shell deferred: desktop UI stdlib currently fails to compile (type clash).
 *
 *   StudioMain --jar <jnative.jar> open <dir>
 *   StudioMain tree | read <path> | write <path> <text>
 *   StudioMain build | run | problems
 *
 * @author Zhang Xiaoda
 */
public class StudioMain {
    static CoreProject current;
    static String jarPath;
    static String lastLogPath;

    static void usage() {
        System.out.println("JNative Studio Core (CLI)");
        System.out.println("usage:");
        System.out.println("  studio --jar <jnative.jar> open <projectDir>");
        System.out.println("  studio tree | read <path> | write <path> <text>");
        System.out.println("  studio build | run | problems");
    }

    static String requireJar(String[] args) {
        int i = 0;
        while (i + 1 < args.length) {
            if (args[i].equals("--jar")) {
                return args[i + 1];
            }
            i = i + 1;
        }
        return "";
    }

    static ArrayList<String> stripJar(String[] args) {
        ArrayList<String> out = new ArrayList<String>();
        int i = 0;
        while (i < args.length) {
            if (args[i].equals("--jar") && i + 1 < args.length) {
                i = i + 2;
            } else {
                out.add(args[i]);
                i = i + 1;
            }
        }
        return out;
    }

    static void cmdOpen(String dir) {
        current = CoreProject.open(dir);
        System.out.println("opened " + current.name + " @ " + current.root);
        File.writeString(".studio-session", current.root);
    }

    static void ensureProject() {
        if (current != null) {
            return;
        }
        if (File.exists(".studio-session")) {
            String root = File.readString(".studio-session");
            int end = root.length();
            if (end > 0 && root.charAt(end - 1) == 10) {
                root = root.substring(0, end - 1);
            }
            current = CoreProject.open(root);
        }
    }

    static void cmdTree() {
        ensureProject();
        if (current == null) {
            System.out.println("no project - run open first");
            return;
        }
        System.out.println("PROJECT " + current.name);
        CoreFs.printTree(current.root, "", 0, 4);
    }

    static void cmdRead(String rel) {
        ensureProject();
        String path = current.root + "/" + rel;
        if (!CoreFs.exists(path)) {
            System.out.println("missing " + rel);
            return;
        }
        System.out.println(CoreFs.readFile(path));
    }

    static void cmdWrite(String rel, String text) {
        ensureProject();
        String path = current.root + "/" + rel;
        CoreFs.writeFile(path, text);
        System.out.println("saved " + rel);
    }

    static void cmdBuild() {
        ensureProject();
        if (current == null) {
            System.out.println("no project");
            return;
        }
        if (jarPath.length() == 0) {
            System.out.println("missing --jar path to jnative.jar");
            return;
        }
        System.out.println("BUILD");
        System.out.println("--------------------------------");
        CoreBuild b = new CoreBuild(jarPath);
        CoreBuildResult r = b.buildProject(current);
        System.out.println(r.stdout);
        System.out.println(r.stderr);
        lastLogPath = ".studio-last-build.log";
        File.writeString(lastLogPath, r.stdout + r.stderr);
        if (r.success == 1) {
            System.out.println("Build succeeded.");
        } else {
            System.out.println("Build failed (exit " + r.exitCode + ").");
            System.out.println("PROBLEMS");
            int i = 0;
            while (i < r.diagnostics.size()) {
                CoreDiag d = r.diagnostics.get(i);
                System.out.println(d.file + ":" + d.line + ":" + d.column);
                System.out.println(d.message);
                i = i + 1;
            }
        }
    }

    static void cmdRun() {
        ensureProject();
        if (current == null) {
            System.out.println("no project");
            return;
        }
        System.out.println("RUN");
        System.out.println("--------------------------------");
        CoreBuild b = new CoreBuild(jarPath);
        CoreBuildResult r = b.runBinary(current);
        System.out.println(r.stdout);
        if (r.stderr.length() > 0) {
            System.out.println(r.stderr);
        }
        System.out.println("Process exited with code " + r.exitCode + ".");
    }

    static void cmdProblems() {
        if (!File.exists(".studio-last-build.log")) {
            System.out.println("(no build log)");
            return;
        }
        ArrayList<CoreDiag> ds = CoreDiagParser.parse(File.readString(".studio-last-build.log"));
        System.out.println("PROBLEMS");
        if (ds.size() == 0) {
            System.out.println("(none parsed)");
            return;
        }
        int i = 0;
        while (i < ds.size()) {
            CoreDiag d = ds.get(i);
            System.out.println(d.file + ":" + d.line + ":" + d.column + " " + d.severity);
            System.out.println(d.message);
            i = i + 1;
        }
    }

    public static void main(String[] args) {
        jarPath = requireJar(args);
        ArrayList<String> a = stripJar(args);
        if (a.size() < 1) {
            usage();
            return;
        }
        String cmd = a.get(0);
        if (cmd.equals("open") && a.size() >= 2) {
            cmdOpen(a.get(1));
            return;
        }
        if (cmd.equals("tree")) {
            cmdTree();
            return;
        }
        if (cmd.equals("read") && a.size() >= 2) {
            cmdRead(a.get(1));
            return;
        }
        if (cmd.equals("write") && a.size() >= 3) {
            cmdWrite(a.get(1), a.get(2));
            return;
        }
        if (cmd.equals("build")) {
            cmdBuild();
            return;
        }
        if (cmd.equals("run")) {
            cmdRun();
            return;
        }
        if (cmd.equals("problems")) {
            cmdProblems();
            return;
        }
        usage();
    }
}
