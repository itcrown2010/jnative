/**
 * Identifier renaming helpers: whole-word replacement that leaves string / char literals and comments alone, and an
 * exact quoted-value replacement for the designer's .jnui JSON.
 *
 * @author 张晓达
 */
public class IdeRename {
    static boolean identChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '$'
                || c > 127;
    }

    /** Replaces every code occurrence of the identifier; {@code count[0]} receives the number of replacements. */
    static String replaceIdent(String text, String from, String to, int[] count) {
        StringBuilder out = new StringBuilder();
        int n = text.length();
        int fl = from.length();
        int i = 0;
        int last = 0;
        int found = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                while (i < n && text.charAt(i) != '\n') {
                    i = i + 1;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                i = i + 2;
                while (i + 1 < n && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) {
                    i = i + 1;
                }
                i = i + 2;
                continue;
            }
            if (c == '"') {
                if (i + 2 < n && text.charAt(i + 1) == '"' && text.charAt(i + 2) == '"') {
                    i = i + 3;
                    while (i + 2 < n && !(text.charAt(i) == '"' && text.charAt(i + 1) == '"' && text.charAt(i + 2) == '"')) {
                        i = i + 1;
                    }
                    i = i + 3;
                    continue;
                }
                i = i + 1;
                while (i < n && text.charAt(i) != '"' && text.charAt(i) != '\n') {
                    if (text.charAt(i) == '\\') {
                        i = i + 1;
                    }
                    i = i + 1;
                }
                i = i + 1;
                continue;
            }
            if (c == '\'') {
                i = i + 1;
                while (i < n && text.charAt(i) != '\'' && text.charAt(i) != '\n') {
                    if (text.charAt(i) == '\\') {
                        i = i + 1;
                    }
                    i = i + 1;
                }
                i = i + 1;
                continue;
            }
            if (identChar(c)) {
                int s = i;
                while (i < n && identChar(text.charAt(i))) {
                    i = i + 1;
                }
                if (i - s == fl && text.substring(s, i).equals(from)) {
                    out.append(text.substring(last, s));
                    out.append(to);
                    last = i;
                    found = found + 1;
                }
                continue;
            }
            i = i + 1;
        }
        if (found == 0) {
            count[0] = 0;
            return text;
        }
        out.append(text.substring(last));
        count[0] = found;
        return out.toString();
    }

    /** In designer JSON: replaces the exact quoted string "from" with "to". */
    static String replaceQuoted(String text, String from, String to, int[] count) {
        String needle = "\"" + from + "\"";
        String rep = "\"" + to + "\"";
        int found = 0;
        StringBuilder out = new StringBuilder();
        int pos = 0;
        while (true) {
            int at = text.indexOf(needle, pos);
            if (at < 0) {
                break;
            }
            out.append(text.substring(pos, at));
            out.append(rep);
            pos = at + needle.length();
            found = found + 1;
        }
        count[0] = found;
        if (found == 0) {
            return text;
        }
        out.append(text.substring(pos));
        return out.toString();
    }
}
