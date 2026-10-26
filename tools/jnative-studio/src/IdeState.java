import jnative.io.*;

/**
 * Per-project IDE state kept in {@code .jnative/studio/state.txt}: run arguments, environment, watch expressions and
 * breakpoints (with optional conditions). One {@code key=value} per line; breakpoints are {@code bp=path|line|cond}.
 *
 * @author 张晓达
 */
public class IdeState {
    String dir = "";
    String args = "";
    ArrayList<String> env = new ArrayList<String>();
    ArrayList<String> watches = new ArrayList<String>();
    /** Bookmarks: file + 0-based line, persisted as {@code bm=path|line}. */
    ArrayList<String> bmPaths = new ArrayList<String>();
    ArrayList<Integer> bmLines = new ArrayList<Integer>();

    // Run configurations. args / env above are the working copy of the current one (cur); the arrays hold the rest.
    ArrayList<String> cfgNames = new ArrayList<String>();
    ArrayList<String> cfgMains = new ArrayList<String>();
    ArrayList<String> cfgArgs = new ArrayList<String>();
    /** Environment entries of each configuration joined with a newline. */
    ArrayList<String> cfgEnvs = new ArrayList<String>();
    int cur = 0;

    static String file(String projectDir) {
        return projectDir + "/.jnative/studio/state.txt";
    }

    /** Loads state for a project; breakpoints are appended to the three parallel lists. */
    void load(String projectDir, ArrayList<String> bpPaths, ArrayList<Integer> bpLines, ArrayList<String> bpConds) {
        dir = projectDir;
        args = "";
        env = new ArrayList<String>();
        watches = new ArrayList<String>();
        bmPaths = new ArrayList<String>();
        bmLines = new ArrayList<Integer>();
        cfgNames = new ArrayList<String>();
        cfgMains = new ArrayList<String>();
        cfgArgs = new ArrayList<String>();
        cfgEnvs = new ArrayList<String>();
        cur = 0;
        FsPath f = FsPath.of(file(projectDir));
        if (!Files.exists(f)) {
            ensureDefault();
            return;
        }
        String curName = "";
        ArrayList<String> lines = DsGen.splitLines(Files.readString(f));
        int i = 0;
        while (i < lines.size()) {
            String ln = lines.get(i);
            int eq = ln.indexOf("=");
            if (eq > 0) {
                String k = ln.substring(0, eq);
                String v = ln.substring(eq + 1);
                if (k.equals("args")) {
                    args = v;
                } else if (k.equals("env") && v.length() > 0) {
                    env.add(v);
                } else if (k.equals("watch") && v.length() > 0) {
                    watches.add(v);
                } else if (k.equals("bm")) {
                    int bar = v.lastIndexOf("|");
                    int ln2 = bar > 0 ? DsUtil.parseInt(v.substring(bar + 1), -1) : -1;
                    if (ln2 >= 0 && Files.exists(FsPath.of(v.substring(0, bar)))) {
                        bmPaths.add(v.substring(0, bar));
                        bmLines.add(ln2);
                    }
                } else if (k.equals("bp")) {
                    addBreakpoint(v, bpPaths, bpLines, bpConds);
                } else if (k.equals("config")) {
                    int a = v.indexOf("|");
                    int b = a < 0 ? -1 : v.indexOf("|", a + 1);
                    if (a > 0 && b > a) {
                        cfgNames.add(v.substring(0, a));
                        cfgMains.add(v.substring(a + 1, b));
                        cfgArgs.add(v.substring(b + 1));
                        cfgEnvs.add("");
                    }
                } else if (k.equals("cenv")) {
                    int a = v.indexOf("|");
                    if (a > 0) {
                        int idx = cfgNames.indexOf(v.substring(0, a));
                        if (idx >= 0) {
                            String old = cfgEnvs.get(idx);
                            cfgEnvs.set(idx, old.length() == 0 ? v.substring(a + 1) : old + "\n" + v.substring(a + 1));
                        }
                    }
                } else if (k.equals("cur")) {
                    curName = v;
                }
            }
            i = i + 1;
        }
        if (cfgNames.size() == 0) {
            // state written before run configurations existed: args / env belong to the default configuration
            ensureDefault();
            cfgArgs.set(0, args);
            cfgEnvs.set(0, joinEnv(env));
        } else {
            int idx = cfgNames.indexOf(curName);
            cur = idx < 0 ? 0 : idx;
            loadCurrent();
        }
    }

    static String joinEnv(ArrayList<String> e) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < e.size()) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(e.get(i));
            i = i + 1;
        }
        return sb.toString();
    }

    void ensureDefault() {
        if (cfgNames.size() == 0) {
            cfgNames.add("默认");
            cfgMains.add("");
            cfgArgs.add("");
            cfgEnvs.add("");
            cur = 0;
        }
    }

    /** Copies the stored current configuration into the working args / env. */
    void loadCurrent() {
        args = cfgArgs.get(cur);
        env = new ArrayList<String>();
        String e = cfgEnvs.get(cur);
        if (e.length() > 0) {
            env = DsGen.splitLines(e);
        }
    }

    /** Stores the working args / env back into the current configuration. */
    void syncCurrent() {
        ensureDefault();
        cfgArgs.set(cur, args);
        cfgEnvs.set(cur, joinEnv(env));
    }

    String curName() {
        ensureDefault();
        return cfgNames.get(cur);
    }

    /** Main source file of the current configuration (absolute path or empty = the project's own entry). */
    String curMain() {
        ensureDefault();
        return cfgMains.get(cur);
    }

    void setCurMain(String m) {
        ensureDefault();
        cfgMains.set(cur, m);
    }

    void select(int i) {
        syncCurrent();
        if (i >= 0 && i < cfgNames.size()) {
            cur = i;
            loadCurrent();
        }
    }

    int addConfig(String name, String main) {
        syncCurrent();
        cfgNames.add(name);
        cfgMains.add(main);
        cfgArgs.add("");
        cfgEnvs.add("");
        cur = cfgNames.size() - 1;
        loadCurrent();
        return cur;
    }

    void deleteCurrent() {
        syncCurrent();
        if (cfgNames.size() <= 1) {
            return;
        }
        cfgNames.removeAt(cur);
        cfgMains.removeAt(cur);
        cfgArgs.removeAt(cur);
        cfgEnvs.removeAt(cur);
        cur = 0;
        loadCurrent();
    }

    void addBreakpoint(String v, ArrayList<String> bpPaths, ArrayList<Integer> bpLines, ArrayList<String> bpConds) {
        int a = v.indexOf("|");
        if (a <= 0) {
            return;
        }
        int b = v.indexOf("|", a + 1);
        if (b < 0) {
            return;
        }
        String path = v.substring(0, a);
        int line = DsUtil.parseInt(v.substring(a + 1, b), -1);
        if (line < 0 || !Files.exists(FsPath.of(path))) {
            return;
        }
        bpPaths.add(path);
        bpLines.add(line);
        bpConds.add(v.substring(b + 1));
    }

    void save(ArrayList<String> bpPaths, ArrayList<Integer> bpLines, ArrayList<String> bpConds) {
        if (dir.length() == 0) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        syncCurrent();
        int i = 0;
        while (i < cfgNames.size()) {
            sb.append("config=" + cfgNames.get(i) + "|" + cfgMains.get(i) + "|" + cfgArgs.get(i) + "\n");
            if (cfgEnvs.get(i).length() > 0) {
                ArrayList<String> es = DsGen.splitLines(cfgEnvs.get(i));
                int k = 0;
                while (k < es.size()) {
                    sb.append("cenv=" + cfgNames.get(i) + "|" + es.get(k) + "\n");
                    k = k + 1;
                }
            }
            i = i + 1;
        }
        sb.append("cur=" + cfgNames.get(cur) + "\n");
        i = 0;
        while (i < watches.size()) {
            sb.append("watch=" + watches.get(i) + "\n");
            i = i + 1;
        }
        i = 0;
        while (i < bmPaths.size()) {
            sb.append("bm=" + bmPaths.get(i) + "|" + bmLines.get(i) + "\n");
            i = i + 1;
        }
        i = 0;
        while (i < bpPaths.size()) {
            sb.append("bp=" + bpPaths.get(i) + "|" + bpLines.get(i) + "|" + bpConds.get(i) + "\n");
            i = i + 1;
        }
        Files.createDirectories(FsPath.of(dir + "/.jnative/studio"));
        Files.writeString(FsPath.of(file(dir)), sb.toString());
    }

    // ---------------------------------------------------------------- arguments

    /** Splits the argument line into words; 'single' and "double" quotes group, backslash escapes the next char. */
    ArrayList<String> argList() {
        ArrayList<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean have = false;
        int quote = 0;
        int i = 0;
        while (i < args.length()) {
            int c = args.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    cur.append(args.substring(i, i + 1));
                }
            } else if (c == 39 || c == 34) {
                quote = c;
                have = true;
            } else if (c == 92 && i + 1 < args.length()) {
                i = i + 1;
                cur.append(args.substring(i, i + 1));
                have = true;
            } else if (c == 32 || c == 9) {
                if (have || cur.length() > 0) {
                    out.add(cur.toString());
                    cur = new StringBuilder();
                    have = false;
                }
            } else {
                cur.append(args.substring(i, i + 1));
                have = true;
            }
            i = i + 1;
        }
        if (have || cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    static String shq(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    /** {@code K='v' K2='v2' } prefix for a shell command (empty when no variables). */
    String shellEnv() {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < env.size()) {
            String e = env.get(i);
            int eq = e.indexOf("=");
            if (eq > 0) {
                sb.append(e.substring(0, eq) + "=" + shq(e.substring(eq + 1)) + " ");
            }
            i = i + 1;
        }
        return sb.toString();
    }

    /** Quoted arguments for a shell command line ({@code ' a' 'b c'}), leading space included. */
    String shellArgs() {
        ArrayList<String> a = argList();
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < a.size()) {
            sb.append(" " + shq(a.get(i)));
            i = i + 1;
        }
        return sb.toString();
    }

    /** JSON array text of the arguments for a DAP launch request. */
    String jsonArgs() {
        ArrayList<String> a = argList();
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int i = 0;
        while (i < a.size()) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(IdeLsp.jstr(a.get(i)));
            i = i + 1;
        }
        sb.append("]");
        return sb.toString();
    }

    String jsonEnv() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int n = 0;
        int i = 0;
        while (i < env.size()) {
            if (env.get(i).indexOf("=") > 0) {
                if (n > 0) {
                    sb.append(",");
                }
                sb.append(IdeLsp.jstr(env.get(i)));
                n = n + 1;
            }
            i = i + 1;
        }
        sb.append("]");
        return sb.toString();
    }

    /** One-line text of the environment ({@code A=1 B=2}) for the edit dialog. */
    String envText() {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < env.size()) {
            if (i > 0) {
                sb.append(" ");
            }
            sb.append(env.get(i));
            i = i + 1;
        }
        return sb.toString();
    }

    void setEnvText(String t) {
        env = new ArrayList<String>();
        IdeState tmp = new IdeState();
        tmp.args = t;
        ArrayList<String> w = tmp.argList();
        int i = 0;
        while (i < w.size()) {
            if (w.get(i).indexOf("=") > 0) {
                env.add(w.get(i));
            }
            i = i + 1;
        }
    }
}
