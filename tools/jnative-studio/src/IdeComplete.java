import jnative.ui.*;
import jnative.io.*;

/**
 * One member (field or method) of a class known to the completion engine.
 *
 * @author 张晓达
 */
class IdeMem {
    String name;
    String type;
    String params;
    boolean method;
    boolean isStatic;
    String owner;

    String label() {
        if (method) {
            return name + "(" + params + ")";
        }
        return name;
    }

    String insertText() {
        if (!method) {
            return name;
        }
        if (params.trim().length() == 0) {
            return name + "()";
        }
        return name + "(";
    }
}

/**
 * A class / interface declaration found in the JNative stdlib or the project sources.
 *
 * @author 张晓达
 */
class IdeCls {
    String name;
    String base = "";
    ArrayList<IdeMem> members = new ArrayList<IdeMem>();
}

/**
 * Class table with insertion-ordered names.
 *
 * @author 张晓达
 */
class IdeTable {
    HashMap<String, IdeCls> map = new HashMap<String, IdeCls>();
    ArrayList<String> names = new ArrayList<String>();

    IdeCls get(String n) {
        return map.get(n);
    }

    void put(String n, IdeCls c) {
        if (!map.containsKey(n)) {
            names.add(n);
        }
        map.put(n, c);
    }
}

/**
 * Lightweight code completion. It does not run the compiler: it indexes `class` declarations and their
 * 4-space-indented members in the stdlib sources plus the project's own files with a line scanner, and
 * resolves `a.b().` chains from variable declarations in the current file.
 *
 * @author 张晓达
 */
public class IdeComplete {
    static IdeTable stdlib = null;
    /** Absolute project source directories, set by the IDE before completing. */
    static ArrayList<String> projectDirs = new ArrayList<String>();
    static String stdlibFor = "";
    static final String[] KEYWORDS = {
        "abstract", "boolean", "break", "byte", "case", "catch", "char", "class", "continue", "default", "do",
        "double", "else", "enum", "extends", "false", "final", "finally", "float", "for", "if", "implements",
        "import", "instanceof", "int", "interface", "long", "new", "null", "package", "private", "protected",
        "public", "return", "short", "static", "super", "switch", "this", "throw", "throws", "true", "try",
        "void", "while"
    };

    /** Classes the compiler provides itself (no stdlib source file). */
    static final String BUILTIN =
            "class Object {\n"
            + "    String toString() {\n    boolean equals(Object other) {\n    int hashCode() {\n}\n"
            + "class String {\n"
            + "    int length() {\n    char charAt(int index) {\n    String substring(int begin, int end) {\n"
            + "    int indexOf(String s) {\n    int lastIndexOf(String s) {\n    boolean startsWith(String s) {\n"
            + "    boolean endsWith(String s) {\n    boolean contains(String s) {\n    boolean equals(Object o) {\n"
            + "    boolean equalsIgnoreCase(String s) {\n    boolean isEmpty() {\n    boolean isBlank() {\n"
            + "    String trim() {\n    String strip() {\n    String toLowerCase() {\n    String toUpperCase() {\n"
            + "    String replace(String from, String to) {\n    String repeat(int count) {\n    String concat(String s) {\n"
            + "    String[] split(String regex) {\n    char[] toCharArray() {\n    byte[] getBytes() {\n"
            + "    int compareTo(String o) {\n    int compareToIgnoreCase(String o) {\n}\n";

    // ------------------------------------------------------------------ lexical helpers

    static boolean isIdent(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '$';
    }

    static boolean isUpper(char c) {
        return c >= 'A' && c <= 'Z';
    }

    /** Identifier characters directly before offset. */
    static String prefixAt(String text, int off) {
        int s = off;
        while (s > 0 && isIdent(text.charAt(s - 1))) {
            s = s - 1;
        }
        return text.substring(s, off);
    }

    static String stripGenerics(String t) {
        int lt = t.indexOf("<");
        if (lt >= 0) {
            t = t.substring(0, lt);
        }
        t = t.trim();
        while (t.endsWith("[]")) {
            t = t.substring(0, t.length() - 2);
        }
        return t;
    }

    static boolean isModifier(String t) {
        return t.equals("public") || t.equals("static") || t.equals("final") || t.equals("abstract")
                || t.equals("native") || t.equals("synchronized") || t.equals("protected") || t.equals("default");
    }

    /** Splits on single spaces, ignoring spaces nested inside <...>. */
    static ArrayList<String> tokens(String s) {
        ArrayList<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '<') {
                depth = depth + 1;
            } else if (c == '>') {
                depth = depth - 1;
            }
            if (c == ' ' && depth <= 0) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur = new StringBuilder();
                }
            } else {
                cur.append(c);
            }
            i = i + 1;
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    // ------------------------------------------------------------------ indexing

    /** Parses all classes in one source text into the table. */
    static void indexText(String text, IdeTable table) {
        IdeCls cur = null;
        int pos = 0;
        int n = text.length();
        while (pos < n) {
            int eol = text.indexOf("\n", pos);
            if (eol < 0) {
                eol = n;
            }
            String line = text.substring(pos, eol);
            pos = eol + 1;
            if (line.length() < 5) {
                continue;
            }
            char c0 = line.charAt(0);
            if (c0 != ' ' && c0 != '\t' && c0 != '}' && c0 != '/' && c0 != '*' && c0 != '@') {
                IdeCls decl = parseClassLine(line);
                if (decl != null) {
                    IdeCls prev = table.get(decl.name);
                    if (prev != null && prev.members.size() > 0 && decl.base.length() == 0) {
                        decl.base = prev.base;
                    }
                    table.put(decl.name, decl);
                    cur = decl;
                }
                continue;
            }
            if (cur != null && c0 == ' ' && line.startsWith("    ") && line.charAt(4) != ' ') {
                parseMember(line.substring(4), cur);
            }
        }
    }

    static IdeCls parseClassLine(String line) {
        ArrayList<String> tk = tokens(line);
        int i = 0;
        while (i < tk.size() && isModifier(tk.get(i))) {
            i = i + 1;
        }
        if (i + 1 >= tk.size()) {
            return null;
        }
        String kw = tk.get(i);
        if (!kw.equals("class") && !kw.equals("interface") && !kw.equals("enum")) {
            return null;
        }
        IdeCls c = new IdeCls();
        String nm = tk.get(i + 1);
        int cut = nm.length();
        int k = 0;
        while (k < nm.length()) {
            if (!isIdent(nm.charAt(k))) {
                cut = k;
                break;
            }
            k = k + 1;
        }
        c.name = nm.substring(0, cut);
        int j = i + 2;
        while (j + 1 < tk.size()) {
            if (tk.get(j).equals("extends")) {
                String b = tk.get(j + 1);
                int comma = b.indexOf(",");
                if (comma >= 0) {
                    b = b.substring(0, comma);
                }
                c.base = stripGenerics(b);
                break;
            }
            j = j + 1;
        }
        return c;
    }

    static void parseMember(String line, IdeCls cls) {
        line = line.trim();
        if (line.length() == 0) {
            return;
        }
        char f = line.charAt(0);
        if (f == '}' || f == '@' || f == '/' || f == '*' || f == '{') {
            return;
        }
        if (line.startsWith("private ") || line.startsWith("return ") || line.startsWith("static {")) {
            return;
        }
        boolean endsOk = line.endsWith("{") || line.endsWith(";");
        if (!endsOk) {
            return;
        }
        int paren = line.indexOf("(");
        int eq = line.indexOf("=");
        IdeMem m = new IdeMem();
        m.owner = cls.name;
        if (paren >= 0 && (eq < 0 || eq > paren)) {
            String head = line.substring(0, paren).trim();
            int close = line.lastIndexOf(")");
            if (close < paren) {
                return;
            }
            m.params = line.substring(paren + 1, close);
            m.method = true;
            ArrayList<String> tk = tokens(head);
            ArrayList<String> rest = new ArrayList<String>();
            int i = 0;
            while (i < tk.size()) {
                String t = tk.get(i);
                if (t.equals("static")) {
                    m.isStatic = true;
                } else if (!isModifier(t)) {
                    rest.add(t);
                }
                i = i + 1;
            }
            if (rest.size() > 0 && rest.get(0).startsWith("<")) {
                rest.removeAt(0);
            }
            if (rest.size() < 2) {
                return; // constructor
            }
            m.name = rest.get(rest.size() - 1);
            m.type = stripGenerics(rest.get(rest.size() - 2));
        } else {
            int end = eq >= 0 ? eq : line.indexOf(";");
            if (end < 0) {
                return;
            }
            ArrayList<String> tk = tokens(line.substring(0, end).trim());
            ArrayList<String> rest = new ArrayList<String>();
            int i = 0;
            while (i < tk.size()) {
                String t = tk.get(i);
                if (t.equals("static")) {
                    m.isStatic = true;
                } else if (!isModifier(t)) {
                    rest.add(t);
                }
                i = i + 1;
            }
            if (rest.size() != 2) {
                return;
            }
            m.method = false;
            m.params = "";
            m.name = rest.get(1);
            m.type = stripGenerics(rest.get(0));
        }
        if (m.name.length() == 0 || !isIdent(m.name.charAt(0))) {
            return;
        }
        cls.members.add(m);
    }

    /** Directory holding the stdlib sources: the compiler's classes dir, or an extract of the jar. */
    static String stdlibDir() {
        String comp = IdeSettings.compiler;
        if (comp.length() == 0) {
            return "";
        }
        if (!comp.endsWith(".jar")) {
            return comp + "/stdlib";
        }
        String dir = "/tmp/jnative-studio-stdlib";
        if (!Files.exists(FsPath.of(dir + "/stdlib"))) {
            ProcessBuilder pb = new ProcessBuilder("/bin/sh");
            pb.argument("-c");
            pb.argument("mkdir -p '" + dir + "' && unzip -q -o '" + comp + "' 'stdlib/*' -d '" + dir + "' >/dev/null 2>&1");
            Process p = pb.start();
            p.waitFor();
        }
        return dir + "/stdlib";
    }

    static IdeTable stdlibTable() {
        String dir = stdlibDir();
        if (stdlib != null && stdlibFor.equals(dir)) {
            return stdlib;
        }
        IdeTable t = new IdeTable();
        if (dir.length() > 0 && Files.exists(FsPath.of(dir))) {
            loadDir(dir, t);
        }
        indexText(BUILTIN, t);
        stdlib = t;
        stdlibFor = dir;
        return t;
    }

    static void loadDir(String dir, IdeTable t) {
        ArrayList<FsPath> all = Files.walk(FsPath.of(dir));
        int i = 0;
        while (i < all.size()) {
            String p = all.get(i).toString();
            if (p.endsWith(".java") && Files.isFile(all.get(i))) {
                indexText(Files.readString(all.get(i)), t);
            }
            i = i + 1;
        }
    }

    /** stdlib + project sources + the (possibly unsaved) text of the current file. */
    static IdeTable buildTable(String text) {
        IdeTable t = new IdeTable();
        IdeTable lib = stdlibTable();
        int i = 0;
        while (i < lib.names.size()) {
            t.put(lib.names.get(i), lib.get(lib.names.get(i)));
            i = i + 1;
        }
        int d = 0;
        while (d < projectDirs.size()) {
            loadDir(projectDirs.get(d), t);
            d = d + 1;
        }
        indexText(text, t);
        return t;
    }

    // ------------------------------------------------------------------ resolution

    static ArrayList<IdeMem> chainMembers(IdeTable t, String cls) {
        ArrayList<IdeMem> out = new ArrayList<IdeMem>();
        int guard = 0;
        String c = cls;
        while (c != null && c.length() > 0 && guard < 12) {
            IdeCls k = t.get(c);
            if (k == null) {
                break;
            }
            int i = 0;
            while (i < k.members.size()) {
                out.add(k.members.get(i));
                i = i + 1;
            }
            c = k.base;
            guard = guard + 1;
        }
        return out;
    }

    static IdeMem findMember(IdeTable t, String cls, String name) {
        ArrayList<IdeMem> ms = chainMembers(t, cls);
        int i = 0;
        while (i < ms.size()) {
            if (ms.get(i).name.equals(name)) {
                return ms.get(i);
            }
            i = i + 1;
        }
        return null;
    }

    /** Type token written before the identifier at `at`, or "" when the context is not a declaration. */
    static String declaredTypeBefore(String text, int at) {
        int p = at - 1;
        while (p >= 0 && text.charAt(p) == ' ') {
            p = p - 1;
        }
        if (p < 0 || p == at - 1) {
            return "";
        }
        int e = p + 1;
        if (text.charAt(p) == '>') {
            int depth = 0;
            while (p >= 0) {
                char c = text.charAt(p);
                if (c == '>') {
                    depth = depth + 1;
                } else if (c == '<') {
                    depth = depth - 1;
                    if (depth == 0) {
                        break;
                    }
                }
                p = p - 1;
            }
            if (p <= 0) {
                return "";
            }
            e = p;
            p = p - 1;
        } else if (text.charAt(p) == ']') {
            while (p >= 0 && text.charAt(p) != '[') {
                p = p - 1;
            }
            e = p;
            p = p - 1;
        }
        int s = p + 1;
        while (p >= 0 && isIdent(text.charAt(p))) {
            p = p - 1;
        }
        s = p + 1;
        if (s >= e) {
            return "";
        }
        String tk = text.substring(s, e);
        if (tk.equals("return") || tk.equals("new") || tk.equals("else") || tk.equals("throw")) {
            return "";
        }
        return tk;
    }

    /** Declared type of a local / parameter / field named `name`, scanning the text before `upto`. */
    static String varType(String text, String name, int upto) {
        String found = "";
        int from = 0;
        int guard = 0;
        while (guard < 4000) {
            int i = text.indexOf(name, from);
            if (i < 0 || i > upto) {
                break;
            }
            guard = guard + 1;
            from = i + name.length();
            boolean startOk = i == 0 || !isIdent(text.charAt(i - 1));
            boolean endOk = from >= text.length() || !isIdent(text.charAt(from));
            if (!startOk || !endOk) {
                continue;
            }
            int q = from;
            while (q < text.length() && text.charAt(q) == ' ') {
                q = q + 1;
            }
            if (q >= text.length()) {
                continue;
            }
            char nx = text.charAt(q);
            if (nx != '=' && nx != ';' && nx != ',' && nx != ')' && nx != ':') {
                continue;
            }
            if (nx == '=' && q + 1 < text.length() && text.charAt(q + 1) == '=') {
                continue;
            }
            String ty = declaredTypeBefore(text, i);
            if (ty.length() > 0 && (isUpper(ty.charAt(0)) || ty.equals("int") || ty.equals("long")
                    || ty.equals("double") || ty.equals("boolean") || ty.equals("char") || ty.equals("float"))) {
                found = ty;
            }
        }
        return found;
    }

    /** The enclosing class: the last class declaration that starts before the caret. */
    static String enclosingClass(String text, int caret) {
        String best = "";
        int pos = 0;
        while (pos < caret) {
            int eol = text.indexOf("\n", pos);
            if (eol < 0) {
                eol = text.length();
            }
            String line = text.substring(pos, eol);
            if (line.length() > 0 && line.charAt(0) != ' ' && line.charAt(0) != '\t') {
                IdeCls c = parseClassLine(line);
                if (c != null) {
                    best = c.name;
                }
            }
            pos = eol + 1;
        }
        return best;
    }

    /** Resolves the type of a dotted expression such as `lblMessage`, `this.btn`, `list.get(0)`. */
    static String typeOfExpr(IdeTable t, String expr, String text, String cur, int caret) {
        ArrayList<String> segs = splitChain(expr);
        if (segs.size() == 0) {
            return "";
        }
        String type = "";
        int i = 0;
        while (i < segs.size()) {
            String seg = segs.get(i);
            boolean call = seg.indexOf("(") >= 0;
            String nm = call ? seg.substring(0, seg.indexOf("(")) : seg;
            nm = nm.trim();
            if (i == 0) {
                if (nm.equals("this")) {
                    type = cur;
                } else if (nm.startsWith("new ")) {
                    type = stripGenerics(nm.substring(4).trim());
                } else if (nm.length() > 0 && nm.charAt(0) == '"') {
                    type = "String";
                } else if (call) {
                    IdeMem m = findMember(t, cur, nm);
                    type = m == null ? "" : m.type;
                } else {
                    String v = varType(text, nm, caret);
                    if (v.length() > 0) {
                        type = stripGenerics(v);
                    } else {
                        IdeMem m = findMember(t, cur, nm);
                        if (m != null && !m.method) {
                            type = m.type;
                        } else if (t.get(nm) != null) {
                            type = nm;
                        }
                    }
                }
            } else {
                IdeMem m = findMember(t, type, nm);
                type = m == null ? "" : m.type;
            }
            if (type.length() == 0) {
                return "";
            }
            i = i + 1;
        }
        return type;
    }

    /** Splits `a.b(c.d).e` into top-level segments, keeping argument lists inside the segment. */
    static ArrayList<String> splitChain(String s) {
        ArrayList<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '(') {
                depth = depth + 1;
            } else if (c == ')') {
                depth = depth - 1;
            }
            if (c == '.' && depth == 0) {
                out.add(cur.toString());
                cur = new StringBuilder();
            } else {
                cur.append(c);
            }
            i = i + 1;
        }
        out.add(cur.toString());
        return out;
    }

    /** The expression (without the trailing dot) that ends right before `dot`. */
    static String exprBefore(String text, int dot) {
        int p = dot - 1;
        int depth = 0;
        while (p >= 0) {
            char c = text.charAt(p);
            if (c == ')') {
                depth = depth + 1;
            } else if (c == '(') {
                if (depth == 0) {
                    break;
                }
                depth = depth - 1;
            } else if (depth == 0 && !isIdent(c) && c != '.') {
                break;
            }
            p = p - 1;
        }
        return text.substring(p + 1, dot);
    }

    // ------------------------------------------------------------------ public entry

    /**
     * Candidates for the caret position. `prefixOut[0]` receives the identifier prefix typed so far.
     */
    static ArrayList<IdeItem> candidates(String text, int caret, String[] prefixOut) {
        ArrayList<IdeItem> out = new ArrayList<IdeItem>();
        String prefix = prefixAt(text, caret);
        prefixOut[0] = prefix;
        int start = caret - prefix.length();
        IdeTable t = buildTable(text);
        String cur = enclosingClass(text, caret);
        HashMap<String, String> seen = new HashMap<String, String>();
        if (start > 0 && text.charAt(start - 1) == '.') {
            String expr = exprBefore(text, start - 1);
            String type = typeOfExpr(t, expr, text, cur, caret);
            if (type.length() == 0) {
                return out;
            }
            boolean staticOnly = t.get(expr) != null && varType(text, expr, caret).length() == 0
                    && findMember(t, cur, expr) == null;
            ArrayList<IdeMem> ms = chainMembers(t, type);
            addMembers(out, ms, seen, staticOnly);
            sortItems(out);
            return out;
        }
        ArrayList<IdeMem> own = chainMembers(t, cur);
        addMembers(out, own, seen, false);
        addWords(out, text, seen, prefix);
        ArrayList<String> names = t.names;
        int i = 0;
        while (i < names.size()) {
            String nm = names.get(i);
            if (!seen.containsKey("c:" + nm) && nm.length() > 1 && isUpper(nm.charAt(0))) {
                seen.put("c:" + nm, "1");
                IdeItem it = new IdeItem(nm, "cc", nm);
                it.hint = "类";
                out.add(it);
            }
            i = i + 1;
        }
        i = 0;
        while (i < KEYWORDS.length) {
            IdeItem it = new IdeItem(KEYWORDS[i], "ck", KEYWORDS[i]);
            it.hint = "关键字";
            out.add(it);
            i = i + 1;
        }
        return out;
    }

    /** Methods first (they are the public API), then fields; each group alphabetical. */
    static void sortItems(ArrayList<IdeItem> a) {
        int i = 1;
        while (i < a.size()) {
            IdeItem x = a.get(i);
            String xk = (x.tag == 1 ? "0" : "1") + x.text.toLowerCase();
            int j = i - 1;
            while (j >= 0 && ((a.get(j).tag == 1 ? "0" : "1") + a.get(j).text.toLowerCase()).compareTo(xk) > 0) {
                a.set(j + 1, a.get(j));
                j = j - 1;
            }
            a.set(j + 1, x);
            i = i + 1;
        }
    }

    static void addMembers(ArrayList<IdeItem> out, ArrayList<IdeMem> ms, HashMap<String, String> seen, boolean staticOnly) {
        int i = 0;
        while (i < ms.size()) {
            IdeMem m = ms.get(i);
            i = i + 1;
            if (staticOnly && !m.isStatic) {
                continue;
            }
            String key = (m.method ? "m:" : "f:") + m.label();
            if (seen.containsKey(key)) {
                continue;
            }
            seen.put(key, "1");
            IdeItem it = new IdeItem(m.label(), m.method ? "cm" : "cf", m.insertText());
            it.hint = m.type + "  · " + m.owner;
            it.tag = m.method ? 1 : 0;
            out.add(it);
        }
    }

    /** Identifiers already used in the file (3+ characters) — cheap local-variable completion. */
    static void addWords(ArrayList<IdeItem> out, String text, HashMap<String, String> seen, String prefix) {
        int n = text.length();
        int i = 0;
        int count = 0;
        while (i < n && count < 400) {
            if (isIdent(text.charAt(i)) && (i == 0 || !isIdent(text.charAt(i - 1)))) {
                int j = i;
                while (j < n && isIdent(text.charAt(j))) {
                    j = j + 1;
                }
                String w = text.substring(i, j);
                if (w.length() >= 3 && !isUpper(w.charAt(0)) && !(w.charAt(0) >= '0' && w.charAt(0) <= '9')
                        && !seen.containsKey("w:" + w) && !seen.containsKey("f:" + w) && !seen.containsKey("m:" + w + "()")
                        && !w.equals(prefix)) {
                    seen.put("w:" + w, "1");
                    IdeItem it = new IdeItem(w, "cw", w);
                    it.hint = "文件内";
                    out.add(it);
                    count = count + 1;
                }
                i = j;
            } else {
                i = i + 1;
            }
        }
    }

    static boolean matches(String label, String prefix) {
        if (prefix.length() == 0) {
            return true;
        }
        return label.toLowerCase().startsWith(prefix.toLowerCase());
    }
}
