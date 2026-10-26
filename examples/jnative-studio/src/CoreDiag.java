/**
 * Diagnostic model + parser for compiler lines containing:
 * path:line:col: error: message
 * (optional channel prefix such as "default: ")
 * @author Zhang Xiaoda
 */
class CoreDiag {
    String file;
    int line;
    int column;
    String severity;
    String message;

    CoreDiag(String f, int ln, int col, String sev, String msg) {
        file = f;
        line = ln;
        column = col;
        severity = sev;
        message = msg;
    }
}

class CoreDiagParser {
    static ArrayList<CoreDiag> parse(String text) {
        ArrayList<CoreDiag> out = new ArrayList<CoreDiag>();
        if (text == null) {
            return out;
        }
        int start = 0;
        int i = 0;
        int n = text.length();
        while (i <= n) {
            if (i == n || text.charAt(i) == 10) {
                if (i > start) {
                    CoreDiag d = parseLine(text.substring(start, i));
                    if (d != null) {
                        out.add(d);
                    }
                }
                start = i + 1;
            }
            i = i + 1;
        }
        return out;
    }

    static CoreDiag parseLine(String line) {
        /* Find ":digits:digits:" pattern; file is text before that colon-run. */
        int n = line.length();
        int i = 0;
        while (i < n) {
            if (line.charAt(i) == 58) {
                int j = i + 1;
                int lineStart = j;
                while (j < n && line.charAt(j) >= 48 && line.charAt(j) <= 57) {
                    j = j + 1;
                }
                if (j > lineStart && j < n && line.charAt(j) == 58) {
                    int k = j + 1;
                    int colStart = k;
                    while (k < n && line.charAt(k) >= 48 && line.charAt(k) <= 57) {
                        k = k + 1;
                    }
                    if (k > colStart && k < n && line.charAt(k) == 58) {
                        String file = line.substring(0, i);
                        /* strip optional "default: " channel prefix */
                        int sp = lastSpace(file);
                        if (sp >= 0 && sp + 1 < file.length()) {
                            file = file.substring(sp + 1, file.length());
                        }
                        int lineNo = parsePositiveInt(line.substring(lineStart, j));
                        int colNo = parsePositiveInt(line.substring(colStart, k));
                        String rest = line.substring(k + 1, line.length());
                        while (rest.length() > 0 && rest.charAt(0) == 32) {
                            rest = rest.substring(1, rest.length());
                        }
                        String severity = "error";
                        String message = rest;
                        int ec = indexOf(rest, 58, 0);
                        if (ec > 0) {
                            severity = rest.substring(0, ec);
                            message = rest.substring(ec + 1, rest.length());
                            while (message.length() > 0 && message.charAt(0) == 32) {
                                message = message.substring(1, message.length());
                            }
                        }
                        if (lineNo >= 0 && colNo >= 0) {
                            return new CoreDiag(file, lineNo, colNo, severity, message);
                        }
                    }
                }
            }
            i = i + 1;
        }
        return null;
    }

    static int lastSpace(String s) {
        int i = s.length() - 1;
        while (i >= 0) {
            int c = s.charAt(i);
            if (c == 32) {
                return i;
            }
            i = i - 1;
        }
        return -1;
    }

    static int indexOf(String s, int ch, int from) {
        int i = from;
        while (i < s.length()) {
            if (s.charAt(i) == ch) {
                return i;
            }
            i = i + 1;
        }
        return -1;
    }

    static int parsePositiveInt(String s) {
        if (s == null || s.length() == 0) {
            return -1;
        }
        int v = 0;
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c < 48 || c > 57) {
                return -1;
            }
            v = v * 10 + (c - 48);
            i = i + 1;
        }
        return v;
    }
}
