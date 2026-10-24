/**
 * Re-indents Java source by bracket nesting (4 spaces per `{` level): lines that start with `}` dedent, continuation
 * lines inside an open `(` / `[` are indented by 8, `case` labels sit one level inside their `switch` with the
 * statements one further in, block comments (` * ` lines) and text blocks keep their own shape. Brackets inside
 * strings, chars and comments are ignored. Trailing whitespace is trimmed.
 *
 * @author 张晓达
 */
public class IdeFormat {
    /** Open brackets carried across lines: '{', '(' or '['. */
    static ArrayList<int> stack = new ArrayList<int>();
    static int braces = 0;
    static boolean inBlockComment = false;
    static boolean inTextBlock = false;
    /** Result of the last scan: leading `}` count and leading `)` / `]` count. */
    static int leadBrace = 0;
    static int leadParen = 0;

    static String spaces(int n) {
        String s = "";
        int i = 0;
        while (i < n) {
            s = s + " ";
            i = i + 1;
        }
        return s;
    }

    static String rtrim(String s) {
        int e = s.length();
        while (e > 0 && (s.charAt(e - 1) == ' ' || s.charAt(e - 1) == '\t' || s.charAt(e - 1) == '\r')) {
            e = e - 1;
        }
        return s.substring(0, e);
    }

    static String ltrim(String s) {
        int b = 0;
        while (b < s.length() && (s.charAt(b) == ' ' || s.charAt(b) == '\t')) {
            b = b + 1;
        }
        return s.substring(b);
    }

    static void push(int c) {
        stack.add(c);
        if (c == '{') {
            braces = braces + 1;
        }
    }

    static void pop() {
        if (stack.size() == 0) {
            return;
        }
        int c = stack.get(stack.size() - 1);
        stack.removeAt(stack.size() - 1);
        if (c == '{') {
            braces = braces - 1;
        }
    }

    /** Updates the bracket stack with one trimmed line and records its leading closers. */
    static void scan(String t) {
        leadBrace = 0;
        leadParen = 0;
        boolean leading = true;
        int i = 0;
        int n = t.length();
        while (i < n) {
            char c = t.charAt(i);
            if (inBlockComment) {
                if (c == '*' && i + 1 < n && t.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i = i + 2;
                } else {
                    i = i + 1;
                }
                continue;
            }
            if (inTextBlock) {
                if (c == '"' && i + 2 < n && t.charAt(i + 1) == '"' && t.charAt(i + 2) == '"') {
                    inTextBlock = false;
                    i = i + 3;
                } else {
                    i = i + 1;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && t.charAt(i + 1) == '/') {
                return;
            }
            if (c == '/' && i + 1 < n && t.charAt(i + 1) == '*') {
                inBlockComment = true;
                leading = false;
                i = i + 2;
                continue;
            }
            if (c == '"') {
                leading = false;
                if (i + 2 < n && t.charAt(i + 1) == '"' && t.charAt(i + 2) == '"') {
                    inTextBlock = true;
                    i = i + 3;
                    continue;
                }
                i = i + 1;
                while (i < n && t.charAt(i) != '"') {
                    if (t.charAt(i) == '\\') {
                        i = i + 1;
                    }
                    i = i + 1;
                }
                i = i + 1;
                continue;
            }
            if (c == '\'') {
                leading = false;
                i = i + 1;
                while (i < n && t.charAt(i) != '\'') {
                    if (t.charAt(i) == '\\') {
                        i = i + 1;
                    }
                    i = i + 1;
                }
                i = i + 1;
                continue;
            }
            if (c == '{' || c == '(' || c == '[') {
                push(c);
                leading = false;
            } else if (c == '}') {
                pop();
                if (leading) {
                    leadBrace = leadBrace + 1;
                }
            } else if (c == ')' || c == ']') {
                pop();
                if (leading) {
                    leadParen = leadParen + 1;
                }
            } else if (c != ' ' && c != '\t') {
                leading = false;
            }
            i = i + 1;
        }
    }

    static boolean startsWithOperator(String t) {
        if (t.startsWith("&&") || t.startsWith("||") || t.startsWith("==") || t.startsWith("!=")) {
            return true;
        }
        char c = t.charAt(0);
        if (c == '.' || c == '?' || c == '+') {
            return !t.startsWith("++");
        }
        if (c == ':') {
            return true;
        }
        return false;
    }

    static boolean endsWithOperator(String prev) {
        if (prev.length() == 0) {
            return false;
        }
        if (prev.endsWith("->")) {
            return true;
        }
        char c = prev.charAt(prev.length() - 1);
        if (c == '=' || c == '+' || c == '*' || c == '&' || c == '|' || c == '?') {
            return true;
        }
        if (c == '-' && !prev.endsWith("--")) {
            return true;
        }
        if (c == ':' && !isCaseLabel(prev) && !prev.startsWith("?") && prev.indexOf("?") >= 0) {
            return true;
        }
        return false;
    }

    static boolean isSwitchHead(String t) {
        return t.startsWith("switch ") || t.startsWith("switch(") || t.indexOf("= switch ") >= 0
                || t.indexOf("return switch ") >= 0 || t.indexOf("} switch") >= 0;
    }

    static boolean isCaseLabel(String t) {
        return t.startsWith("case ") || t.startsWith("default:") || t.startsWith("default ->")
                || t.equals("default");
    }

    static String reindent(String text) {
        stack = new ArrayList<int>();
        braces = 0;
        inBlockComment = false;
        inTextBlock = false;
        ArrayList<String> lines = new ArrayList<String>();
        int pos = 0;
        int n = text.length();
        while (pos <= n) {
            int eol = text.indexOf("\n", pos);
            if (eol < 0) {
                eol = n;
            }
            lines.add(text.substring(pos, eol));
            pos = eol + 1;
        }
        StringBuilder out = new StringBuilder();
        /* Brace depth of the bodies of the switch statements we are in. */
        ArrayList<int> switchBodies = new ArrayList<int>();
        String prevCode = "";
        int i = 0;
        while (i < lines.size()) {
            String raw = lines.get(i);
            String t = ltrim(rtrim(raw));
            boolean startedInBlock = inBlockComment;
            boolean startedInText = inTextBlock;
            int topAtStart = stack.size() > 0 ? stack.get(stack.size() - 1) : 0;
            int bracesAtStart = braces;
            if (t.length() == 0) {
                out.append("");
            } else if (startedInText) {
                out.append(rtrim(raw));
                scan(t);
            } else {
                scan(t);
                int level = bracesAtStart - leadBrace;
                if (level < 0) {
                    level = 0;
                }
                while (switchBodies.size() > 0 && level < switchBodies.get(switchBodies.size() - 1)) {
                    switchBodies.removeAt(switchBodies.size() - 1);
                }
                int inSwitch = switchBodies.size();
                if (inSwitch > 0 && isCaseLabel(t) && level == switchBodies.get(inSwitch - 1)) {
                    inSwitch = inSwitch - 1;
                }
                int spaceCount = (level + inSwitch) * 4;
                if ((topAtStart == '(' || topAtStart == '[') && leadParen == 0) {
                    spaceCount = spaceCount + 8;
                }
                boolean commentLine = startedInBlock || t.startsWith("//") || t.startsWith("/*");
                if (!commentLine && topAtStart != '(' && topAtStart != '['
                        && (startsWithOperator(t) || endsWithOperator(prevCode))) {
                    spaceCount = spaceCount + 8;
                }
                if (startedInBlock && t.startsWith("*")) {
                    spaceCount = spaceCount + 1;
                }
                if (!commentLine) {
                    prevCode = t;
                }
                out.append(spaces(spaceCount) + t);
                if (t.endsWith("{") && isSwitchHead(t) && braces > bracesAtStart - leadBrace) {
                    switchBodies.add(braces);
                }
            }
            if (i + 1 < lines.size()) {
                out.append("\n");
            }
            i = i + 1;
        }
        return out.toString();
    }
}
