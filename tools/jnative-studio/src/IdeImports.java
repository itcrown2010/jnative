/**
 * Optimize imports: drops single-type imports whose simple name is never used, removes duplicates and sorts the rest
 * (normal imports first, then static imports, a blank line between the two groups). Wildcard imports are kept.
 *
 * @author 张晓达
 */
public class IdeImports {
    static boolean isImportLine(String line) {
        String t = line.trim();
        return t.startsWith("import ") && t.endsWith(";");
    }

    /** Last segment of {@code import a.b.C;} / {@code import static a.b.C.m;}; "*" for wildcards. */
    static String simpleName(String importLine) {
        String t = importLine.trim();
        t = t.substring(7, t.length() - 1).trim();
        if (t.startsWith("static ")) {
            t = t.substring(7).trim();
        }
        int dot = t.lastIndexOf(".");
        return dot < 0 ? t : t.substring(dot + 1).trim();
    }

    static boolean isIdent(int c) {
        return (c >= 48 && c <= 57) || (c >= 65 && c <= 90) || (c >= 97 && c <= 122) || c == 95 || c == 36 || c > 127;
    }

    /** True when {@code name} occurs as a whole identifier in code (comments and string literals do not count). */
    static boolean uses(String code, String name) {
        int from = 0;
        boolean found = false;
        while (!found) {
            int p = code.indexOf(name, from);
            if (p < 0) {
                break;
            }
            boolean before = p == 0 || !isIdent(code.charAt(p - 1));
            int e = p + name.length();
            boolean after = e >= code.length() || !isIdent(code.charAt(e));
            if (before && after) {
                found = true;
            }
            from = p + 1;
        }
        return found;
    }

    /** Blanks out comments and string / char literal contents (keeps line structure). */
    static String codeOnly(String s) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') {
                    i = i + 1;
                }
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                i = i + 2;
                while (i < n && !(s.charAt(i) == '*' && i + 1 < n && s.charAt(i + 1) == '/')) {
                    if (s.charAt(i) == '\n') {
                        sb.append('\n');
                    }
                    i = i + 1;
                }
                i = i + 2;
            } else if (c == '"' || c == '\'') {
                char q = c;
                sb.append(' ');
                i = i + 1;
                while (i < n && s.charAt(i) != q && s.charAt(i) != '\n') {
                    if (s.charAt(i) == '\\') {
                        i = i + 1;
                    }
                    i = i + 1;
                }
                i = i + 1;
            } else {
                sb.append(c);
                i = i + 1;
            }
        }
        return sb.toString();
    }

    static String optimize(String src) {
        boolean crlf = src.indexOf("\r\n") >= 0;
        String text = crlf ? src.replace("\r\n", "\n") : src;
        ArrayList<String> lines = DsGen.splitLines(text);
        int first = -1;
        int last = -1;
        int i = 0;
        while (i < lines.size()) {
            if (isImportLine(lines.get(i))) {
                if (first < 0) {
                    first = i;
                }
                last = i;
            } else if (first >= 0 && lines.get(i).trim().length() > 0) {
                break;
            }
            i = i + 1;
        }
        if (first < 0 || lines.size() == 0) {
            return src;
        }
        StringBuilder body = new StringBuilder();
        i = 0;
        while (i < lines.size()) {
            if (i < first || i > last) {
                body.append(lines.get(i)).append('\n');
            }
            i = i + 1;
        }
        String code = codeOnly(body.toString());
        ArrayList<String> normal = new ArrayList<String>();
        ArrayList<String> statics = new ArrayList<String>();
        i = first;
        while (i <= last) {
            if (isImportLine(lines.get(i))) {
                String t = lines.get(i).trim();
                String sn = simpleName(t);
                boolean keep = sn.equals("*") || uses(code, sn);
                if (keep) {
                    ArrayList<String> dst = t.substring(7).trim().startsWith("static ") ? statics : normal;
                    if (!dst.contains(t)) {
                        dst.add(t);
                    }
                }
            }
            i = i + 1;
        }
        sortStrings(normal);
        sortStrings(statics);
        StringBuilder out = new StringBuilder();
        i = 0;
        while (i < first) {
            out.append(lines.get(i)).append('\n');
            i = i + 1;
        }
        i = 0;
        while (i < normal.size()) {
            out.append(normal.get(i)).append('\n');
            i = i + 1;
        }
        if (normal.size() > 0 && statics.size() > 0) {
            out.append('\n');
        }
        i = 0;
        while (i < statics.size()) {
            out.append(statics.get(i)).append('\n');
            i = i + 1;
        }
        i = last + 1;
        if (normal.size() + statics.size() == 0) {
            while (i < lines.size() && lines.get(i).trim().length() == 0 && i < lines.size() - 1) {
                i = i + 1;
            }
        }
        while (i < lines.size()) {
            out.append(lines.get(i));
            if (i < lines.size() - 1) {
                out.append('\n');
            }
            i = i + 1;
        }
        String res = out.toString();
        return crlf ? res.replace("\n", "\r\n") : res;
    }

    static void sortStrings(ArrayList<String> a) {
        int i = 1;
        while (i < a.size()) {
            String v = a.get(i);
            int j = i - 1;
            while (j >= 0 && a.get(j).compareTo(v) > 0) {
                a.set(j + 1, a.get(j));
                j = j - 1;
            }
            a.set(j + 1, v);
            i = i + 1;
        }
    }
}
