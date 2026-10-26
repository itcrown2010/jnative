/**
 * Dump tokens (same format as io.jnative.selfhost.RefTokenDump).
 * @author Zhang Xiaoda
 */
public class JnLexMain {
    static String escape(String s) {
        if (s == null) {
            return "";
        }
        String out = "";
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c == 92) {
                out = out + "\\\\";
            } else if (c == 10) {
                out = out + "\\n";
            } else if (c == 9) {
                out = out + "\\t";
            } else if (c == 13) {
                out = out + "\\r";
            } else if (c == 0) {
                out = out + "\\0";
            } else {
                byte[] b = new byte[1];
                b[0] = (byte) c;
                out = out + String.fromBytes(b);
            }
            i = i + 1;
        }
        return out;
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("usage: JnLexMain <source-file>");
            return;
        }
        String path = args[0];
        if (!File.exists(path)) {
            System.out.println("missing " + path);
            return;
        }
        String src = File.readString(path);
        String name = path;
        int slash = path.length() - 1;
        while (slash >= 0 && path.charAt(slash) != 47) {
            slash = slash - 1;
        }
        if (slash >= 0) {
            name = path.substring(slash + 1, path.length());
        }
        JnLexer lexer = new JnLexer(src, name);
        ArrayList<JnToken> tokens = lexer.tokenize();
        int i = 0;
        while (i < tokens.size()) {
            JnToken t = tokens.get(i);
            System.out.println(t.type + "\t" + t.line + "\t" + t.column + "\t" + escape(t.lexeme));
            i = i + 1;
        }
    }
}
