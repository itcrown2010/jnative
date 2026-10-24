import jnative.io.*;
import jnative.json.*;

/**
 * Persistent Studio settings: the compiler to build projects with and the recent-project list.
 *
 * @author 张晓达
 */
class IdeSettings {
    static String javaCmd = "java";
    static String compiler = "";
    static ArrayList<String> recent = new ArrayList<String>();
    static boolean loaded = false;
    static int fontSize = 13;

    static FsPath file() {
        FsPath dir = Files.configDir("jnative-studio");
        Files.createDirectories(dir);
        return FsPath.of(dir.toString() + "/settings.txt");
    }

    static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        FsPath f = file();
        if (Files.exists(f)) {
            ArrayList<String> lines = DsGen.splitLines(Files.readString(f));
            int i = 0;
            while (i < lines.size()) {
                String ln = lines.get(i);
                int eq = ln.indexOf("=");
                if (eq > 0) {
                    String k = ln.substring(0, eq);
                    String v = ln.substring(eq + 1);
                    if (k.equals("java")) {
                        javaCmd = v;
                    } else if (k.equals("compiler")) {
                        compiler = v;
                    } else if (k.equals("fontSize")) {
                        fontSize = Math.max(9, Math.min(32, DsUtil.parseInt(v, 13)));
                    } else if (k.equals("recent") && v.length() > 0) {
                        recent.add(v);
                    }
                }
                i = i + 1;
            }
        }
        if (compiler.length() == 0 || !Files.exists(FsPath.of(compiler))) {
            compiler = detectCompiler();
        }
    }

    static void save() {
        StringBuilder sb = new StringBuilder();
        sb.append("java=" + javaCmd + "\n");
        sb.append("compiler=" + compiler + "\n");
        sb.append("fontSize=" + fontSize + "\n");
        int i = 0;
        while (i < recent.size()) {
            sb.append("recent=" + recent.get(i) + "\n");
            i = i + 1;
        }
        Files.writeString(file(), sb.toString());
    }

    static void addRecent(String dir) {
        int idx = recent.indexOf(dir);
        if (idx >= 0) {
            recent.remove(idx);
        }
        recent.add(0, dir);
        while (recent.size() > 8) {
            recent.remove(recent.size() - 1);
        }
        save();
    }

    static String probe(String dir) {
        String classes = dir + "/target/classes";
        if (Files.exists(FsPath.of(classes + "/io/jnative/Main.class"))) {
            return classes;
        }
        String jar = dir + "/target/jnative.jar";
        if (Files.exists(FsPath.of(jar))) {
            return jar;
        }
        return "";
    }

    /** Looks for the JNative compiler: env JNATIVE_COMPILER / JNATIVE_HOME, then up from the working dir. */
    static String detectCompiler() {
        String e = Env.get("JNATIVE_COMPILER");
        if (e != null && e.length() > 0 && Files.exists(FsPath.of(e))) {
            return e;
        }
        String home = Env.get("JNATIVE_HOME");
        if (home != null && home.length() > 0) {
            String r = probe(home);
            if (r.length() > 0) {
                return r;
            }
        }
        String dir = Env.get("PWD");
        int guard = 0;
        while (dir != null && dir.length() > 1 && guard < 8) {
            String r = probe(dir);
            if (r.length() > 0) {
                return r;
            }
            dir = Ide.dirName(dir);
            guard = guard + 1;
        }
        return "";
    }

    static boolean hasCompiler() {
        return compiler.length() > 0;
    }

    /** Shell command that builds the project in the current directory. */
    static String buildCommand() {
        if (compiler.endsWith(".jar")) {
            return javaCmd + " -jar '" + compiler + "' build --project . 2>&1";
        }
        return javaCmd + " -cp '" + compiler + "' io.jnative.Main build --project . 2>&1";
    }
}

/**
 * A JNative project on disk: jnative.project + source folders, with templates for new projects and
 * forms and the explorer tree.
 *
 * @author 张晓达
 */
class IdeProject {
    String dir = "";
    String name = "";
    String mainClass = "Main";
    String output = "";
    ArrayList<String> srcDirs = new ArrayList<String>();

    static String esc(String s) {
        return DsUtil.jsonString(s);
    }

    static IdeProject open(String dir) {
        FsPath pf = FsPath.of(dir + "/jnative.project");
        if (!Files.exists(pf)) {
            return null;
        }
        JsonValue v = Json.parse(Files.readString(pf));
        if (!v.isObject()) {
            return null;
        }
        JsonObject o = v.asObject();
        IdeProject p = new IdeProject();
        p.dir = dir;
        p.name = o.getString("name", Ide.baseName(dir));
        JsonValue mods = o.get("modules");
        if (mods.isObject()) {
            JsonObject mo = mods.asObject();
            int i = 0;
            while (i < mo.keys.size()) {
                JsonValue m = mo.values.get(i);
                if (m.isObject()) {
                    String src = m.asObject().getString("src", "src");
                    if (p.srcDirs.indexOf(src) < 0) {
                        p.srcDirs.add(src);
                    }
                }
                i = i + 1;
            }
        }
        if (p.srcDirs.size() == 0) {
            p.srcDirs.add("src");
        }
        JsonValue ts = o.get("targets");
        if (ts.isArray() && ts.asArray().size() > 0 && ts.asArray().get(0).isObject()) {
            JsonObject t = ts.asArray().get(0).asObject();
            p.mainClass = t.getString("main", "Main");
            p.output = t.getString("output", ".jnative/build/" + p.name);
        } else {
            p.output = ".jnative/build/" + p.name;
        }
        return p;
    }

    String outputPath() {
        if (output.startsWith("/")) {
            return output;
        }
        return dir + "/" + output;
    }

    String srcRoot() {
        return dir + "/" + srcDirs.get(0);
    }

    /** Creates a new project (dir/name) from the Hello template; returns it or null on failure. */
    static IdeProject create(String parentDir, String name) {
        String dir = parentDir + "/" + name;
        if (Files.exists(FsPath.of(dir + "/jnative.project"))) {
            return null;
        }
        Files.createDirectories(FsPath.of(dir + "/src"));
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": " + esc(name) + ",\n");
        sb.append("  \"modules\": {\n");
        sb.append("    \"app\": {\n");
        sb.append("      \"kind\": \"app\",\n");
        sb.append("      \"src\": \"src\"\n");
        sb.append("    }\n");
        sb.append("  },\n");
        sb.append("  \"targets\": [\n");
        sb.append("    {\n");
        sb.append("      \"name\": " + esc(name) + ",\n");
        sb.append("      \"triple\": \"native\",\n");
        sb.append("      \"module\": \"app\",\n");
        sb.append("      \"main\": \"Main\",\n");
        sb.append("      \"output\": " + esc(".jnative/build/" + name) + "\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");
        Files.writeString(FsPath.of(dir + "/jnative.project"), sb.toString());
        Files.writeString(FsPath.of(dir + "/.gitignore"), ".jnative/\n");
        if (IdeDialog.template == 2) {
            Files.writeString(FsPath.of(dir + "/src/Main.java"), consoleMain(name));
            return open(dir);
        }
        DsDoc d = IdeDialog.template == 1 ? DsDoc.blank("MainForm") : DsSample.hello("MainForm");
        writeForm(dir + "/src", d, true);
        String userPath = dir + "/src/MainForm.java";
        String user = Files.readString(FsPath.of(userPath));
        DsEvent ev = DsMeta.find("Button").event("click");
        String stub = DsGen.handlerStub("onBtnHelloClick", ev);
        int todo = stub.indexOf("        // TODO");
        if (todo >= 0) {
            int eol = stub.indexOf("\n", todo);
            stub = stub.substring(0, todo) + "        lblMessage.setText(\"你好，\" + txtName.getText() + \"！\");" + stub.substring(eol);
        }
        if (IdeDialog.template == 0) {
            user = DsGen.insertMethod(user, stub);
            Files.writeString(FsPath.of(userPath), user);
        }
        Files.writeString(FsPath.of(dir + "/src/Main.java"), DsGen.mainStub(d));
        return open(dir);
    }

    static String consoleMain(String name) {
        StringBuilder sb = new StringBuilder();
        sb.append("/**\n * " + name + " 的入口。\n */\n");
        sb.append("public class Main {\n");
        sb.append("    public static void main(String[] args) {\n");
        sb.append("        System.out.println(\"Hello, " + name + "!\");\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    /** Writes Name.jnui, NameBase.java and (when missing) Name.java into srcDir. */
    static void writeForm(String srcDir, DsDoc d, boolean createUser) {
        Files.createDirectories(FsPath.of(srcDir));
        Files.writeString(FsPath.of(srcDir + "/" + d.className + ".jnui"), d.toFileText());
        Files.writeString(FsPath.of(srcDir + "/" + d.className + "Base.java"), DsGen.generateBase(d));
        FsPath user = FsPath.of(srcDir + "/" + d.className + ".java");
        if (createUser && !Files.exists(user)) {
            Files.writeString(user, DsGen.userStub(d));
        }
    }

    static boolean hiddenName(String n) {
        return n.startsWith(".") || n.equals("target") || n.equals("node_modules") || n.equals("build");
    }

    IdeItem buildTree() {
        IdeItem root = new IdeItem("", "", "");
        IdeItem top = new IdeItem(name, "project", dir);
        top.expanded = true;
        top.hint = "项目";
        root.add(top);
        addDir(top, dir, 0);
        return root;
    }

    void addDir(IdeItem parent, String path, int depth) {
        ArrayList<String> names = Files.list(FsPath.of(path));
        ArrayList<String> dirs = new ArrayList<String>();
        ArrayList<String> files = new ArrayList<String>();
        int i = 0;
        while (i < names.size()) {
            String n = names.get(i);
            if (!hiddenName(n)) {
                if (Files.isDirectory(FsPath.of(path + "/" + n))) {
                    dirs.add(n);
                } else {
                    files.add(n);
                }
            }
            i = i + 1;
        }
        sortNames(dirs);
        sortNames(files);
        i = 0;
        while (i < dirs.size()) {
            String n = dirs.get(i);
            IdeItem it = new IdeItem(n, "folder", path + "/" + n);
            it.expanded = depth < 1;
            parent.add(it);
            if (depth < 8) {
                addDir(it, path + "/" + n, depth + 1);
            }
            i = i + 1;
        }
        i = 0;
        while (i < files.size()) {
            String n = files.get(i);
            IdeItem it = new IdeItem(n, Ide.iconForFile(n), path + "/" + n);
            parent.add(it);
            i = i + 1;
        }
    }

    static void sortNames(ArrayList<String> a) {
        int i = 1;
        while (i < a.size()) {
            String key = a.get(i);
            int j = i - 1;
            while (j >= 0 && a.get(j).toLowerCase().compareTo(key.toLowerCase()) > 0) {
                a.set(j + 1, a.get(j));
                j = j - 1;
            }
            a.set(j + 1, key);
            i = i + 1;
        }
    }
}

/**
 * One parsed compiler diagnostic.
 */
class IdeDiag {
    String path = "";
    int line = 1;
    int col = 1;
    int severity = 1; // 1 error, 2 warning
    String message = "";

    /** Parses "module: /path/File.java:12:5: error: message"; null when the line is not a diagnostic. */
    static IdeDiag parse(String ln) {
        int ei = ln.indexOf(": error: ");
        int sev = 1;
        int skip = 9;
        if (ei < 0) {
            ei = ln.indexOf(": warning: ");
            sev = 2;
            skip = 11;
        }
        if (ei < 0) {
            return null;
        }
        String loc = ln.substring(0, ei);
        String msg = ln.substring(ei + skip);
        int c2 = loc.lastIndexOf(":");
        if (c2 < 0) {
            return null;
        }
        int col = DsUtil.parseInt(loc.substring(c2 + 1), -1);
        String rest = loc.substring(0, c2);
        int c1 = rest.lastIndexOf(":");
        if (c1 < 0 || col < 0) {
            return null;
        }
        int line = DsUtil.parseInt(rest.substring(c1 + 1), -1);
        if (line < 0) {
            return null;
        }
        String path = rest.substring(0, c1);
        int sp = path.indexOf(": ");
        if (sp >= 0 && sp < 24 && !path.startsWith("/")) {
            path = path.substring(sp + 2);
        }
        if (path.startsWith("/private/tmp/") || path.startsWith("/private/var/")) {
            path = path.substring(8);
        }
        IdeDiag d = new IdeDiag();
        d.path = path;
        d.line = line;
        d.col = col;
        d.severity = sev;
        d.message = msg;
        return d;
    }
}
