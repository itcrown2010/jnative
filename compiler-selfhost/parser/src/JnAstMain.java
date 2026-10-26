/**
 * Self-host AST dump CLI (lexer + subset parser).
 * @author Zhang Xiaoda
 */
public class JnAstMain {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("usage: JnAstMain <source-file>");
            return;
        }
        String path = args[0];
        if (!File.exists(path)) {
            System.out.println("missing " + path);
            return;
        }
        String src = File.readString(path);
        JnLexer lexer = new JnLexer(src, path);
        ArrayList<JnToken> tokens = lexer.tokenize();
        JnParser parser = JnParser.create(tokens, path);
        System.out.println(parser.parseDump());
    }
}
