/**
 * Subset recursive-descent parser for self-host AST dumps.
 * Dump format aligned with io.jnative.ast.AstPrinter for supported programs.
 * @author Zhang Xiaoda
 */
class JnParser {
    ArrayList<JnToken> tokens;
    int current;
    String fileName;
    String dump;
    int failed;

    JnParser() {
        tokens = new ArrayList<JnToken>();
        current = 0;
        fileName = "";
        dump = "";
        failed = 0;
    }

    static JnParser create(ArrayList<JnToken> toks, String file) {
        JnParser p = new JnParser();
        p.tokens = toks;
        p.current = 0;
        p.fileName = file;
        p.dump = "";
        p.failed = 0;
        return p;
    }

    String parseDump() {
        String head = "CompilationUnit(";
        String mid = fileName;
        if (mid == null) {
            mid = "null";
        }
        String out = head + mid + ")\n";
        current = 0;
        int guard = 0;
        while (guard < 1000) {
            JnToken t = peek();
            if (t.type.equals("EOF")) {
                return out;
            }
            int before = current;
            out = parseTypeDeclInto(out, 1);
            if (current == before) {
                current = current + 1;
            }
            guard = guard + 1;
        }
        return out;
    }

    String parseTypeDeclInto(String out, int indent) {
        dump = out;
        parseTypeDecl(indent);
        return dump;
    }

    void parseTypeDecl(int indent) {
        while (check("PUBLIC") == 1 || check("PRIVATE") == 1 || check("PROTECTED") == 1
                || check("ABSTRACT") == 1 || check("FINAL") == 1 || check("STATIC") == 1) {
            advance();
        }
        if (match("CLASS") == 0 && match("INTERFACE") == 0) {
            fail("expected class");
            synchronize();
            return;
        }
        String name = expectIdent();
        if (match("LT") == 1) {
            skipUntilGt();
        }
        String superName = null;
        if (match("EXTENDS") == 1) {
            superName = expectIdent();
            if (match("LT") == 1) {
                skipUntilGt();
            }
        }
        if (match("IMPLEMENTS") == 1) {
            expectIdent();
            while (match("COMMA") == 1) {
                expectIdent();
            }
        }
        expect("LBRACE");
        lineIndent(indent);
        dump = dump + "Class " + name;
        if (superName != null) {
            dump = dump + " extends " + superName;
        }
        dump = dump + "\n";
        while (check("RBRACE") == 0 && check("EOF") == 0) {
            parseMember(name, indent + 1);
        }
        expect("RBRACE");
    }

    void parseMember(String className, int indent) {
        int isPublic = 0;
        int isStatic = 0;
        while (check("PUBLIC") == 1 || check("PRIVATE") == 1 || check("PROTECTED") == 1
                || check("STATIC") == 1 || check("FINAL") == 1 || check("NATIVE") == 1
                || check("ABSTRACT") == 1 || check("ASYNC") == 1) {
            if (check("PUBLIC") == 1) {
                isPublic = 1;
            }
            if (check("STATIC") == 1) {
                isStatic = 1;
            }
            advance();
        }
        /* constructor */
        if (check("IDENTIFIER") == 1 && peekNextType().equals("LPAREN")) {
            String n = expectIdent();
            if (n.equals(className)) {
                expect("LPAREN");
                finishMethod(indent, isPublic, isStatic, 1, "void", 0, n);
                return;
            }
            fail("expected type");
            synchronize();
            return;
        }
        String ret = parseTypeName();
        int retArray = 0;
        while (match("LBRACKET") == 1) {
            expect("RBRACKET");
            retArray = 1;
        }
        String n = expectIdent();
        if (check("LPAREN") == 1) {
            expect("LPAREN");
            finishMethod(indent, isPublic, isStatic, 0, ret, retArray, n);
            return;
        }
        expect("SEMICOLON");
        lineIndent(indent);
        dump = dump + "Field " + ret;
        if (retArray == 1) {
            dump = dump + "[]";
        }
        dump = dump + " " + n + "\n";
    }

    void finishMethod(int indent, int isPublic, int isStatic, int isCtor, String ret, int retArray, String name) {
        ArrayList<String> params = new ArrayList<String>();
        if (check("RPAREN") == 0) {
            params.add(parseParam());
            while (match("COMMA") == 1) {
                params.add(parseParam());
            }
        }
        expect("RPAREN");
        if (match("THROWS") == 1) {
            expectIdent();
            while (match("COMMA") == 1) {
                expectIdent();
            }
        }
        lineIndent(indent);
        if (isCtor == 1) {
            dump = dump + "ctor " + name + "(";
        } else {
            if (isPublic == 1) {
                dump = dump + "public ";
            }
            if (isStatic == 1) {
                dump = dump + "static ";
            }
            dump = dump + ret;
            if (retArray == 1) {
                dump = dump + "[]";
            }
            dump = dump + " " + name + "(";
        }
        int i = 0;
        while (i < params.size()) {
            if (i > 0) {
                dump = dump + ", ";
            }
            dump = dump + params.get(i);
            i = i + 1;
        }
        dump = dump + ")\n";
        parseBlock(indent + 1);
    }

    String parseParam() {
        String t = parseTypeName();
        int arr = 0;
        while (match("LBRACKET") == 1) {
            expect("RBRACKET");
            arr = 1;
        }
        String n = expectIdent();
        if (arr == 1) {
            return t + "[] " + n;
        }
        return t + " " + n;
    }

    String parseTypeName() {
        String t = expectIdentOrTypeKeyword();
        if (match("LT") == 1) {
            skipUntilGt();
        }
        return t;
    }

    void parseBlock(int indent) {
        expect("LBRACE");
        lineIndent(indent);
        dump = dump + "Block {\n";
        while (check("RBRACE") == 0 && check("EOF") == 0) {
            parseStatement(indent + 1);
        }
        expect("RBRACE");
        lineIndent(indent);
        dump = dump + "}\n";
    }

    void parseStatement(int indent) {
        if (match("RETURN") == 1) {
            lineIndent(indent);
            dump = dump + "Return";
            if (check("SEMICOLON") == 0) {
                dump = dump + " " + parseExpression();
            }
            dump = dump + "\n";
            expect("SEMICOLON");
            return;
        }
        if (match("IF") == 1) {
            expect("LPAREN");
            String cond = parseExpression();
            expect("RPAREN");
            lineIndent(indent);
            dump = dump + "If " + cond + "\n";
            parseStatement(indent + 1);
            if (match("ELSE") == 1) {
                lineIndent(indent);
                dump = dump + "Else\n";
                parseStatement(indent + 1);
            }
            return;
        }
        if (match("WHILE") == 1) {
            expect("LPAREN");
            String cond = parseExpression();
            expect("RPAREN");
            lineIndent(indent);
            dump = dump + "While " + cond + "\n";
            parseStatement(indent + 1);
            return;
        }
        if (check("LBRACE") == 1) {
            parseBlock(indent);
            return;
        }
        if (looksLikeVarDecl() == 1) {
            String t = parseTypeName();
            int arr = 0;
            while (match("LBRACKET") == 1) {
                expect("RBRACKET");
                arr = 1;
            }
            String n = expectIdent();
            String init = null;
            if (match("ASSIGN") == 1) {
                init = parseExpression();
            }
            expect("SEMICOLON");
            lineIndent(indent);
            dump = dump + "Var " + t;
            if (arr == 1) {
                dump = dump + "[]";
            }
            dump = dump + " " + n;
            if (init != null) {
                dump = dump + " = " + init;
            }
            dump = dump + "\n";
            return;
        }
        String e = parseExpression();
        expect("SEMICOLON");
        lineIndent(indent);
        dump = dump + "Expr " + e + "\n";
    }

    int looksLikeVarDecl() {
        int save = current;
        if (isTypeKeywordOrIdent() == 0) {
            return 0;
        }
        advance();
        if (match("LT") == 1) {
            skipUntilGt();
        }
        while (match("LBRACKET") == 1) {
            if (match("RBRACKET") == 0) {
                current = save;
                return 0;
            }
        }
        if (check("IDENTIFIER") == 1) {
            advance();
            int ok = 0;
            if (check("SEMICOLON") == 1 || check("ASSIGN") == 1) {
                ok = 1;
            }
            current = save;
            return ok;
        }
        current = save;
        return 0;
    }

    int isTypeKeywordOrIdent() {
        return check("IDENTIFIER") == 1 || check("INT") == 1 || check("LONG") == 1 || check("FLOAT") == 1
                || check("DOUBLE") == 1 || check("BOOLEAN") == 1 || check("BYTE") == 1 || check("SHORT") == 1
                || check("CHAR") == 1 || check("VOID") == 1 ? 1 : 0;
    }

    String parseExpression() {
        return parseAssignment();
    }

    String parseAssignment() {
        String left = parseOr();
        if (match("ASSIGN") == 1) {
            return "(" + left + " = " + parseAssignment() + ")";
        }
        return left;
    }

    String parseOr() {
        String e = parseAnd();
        while (match("OR") == 1) {
            e = "(" + e + " OR " + parseAnd() + ")";
        }
        return e;
    }

    String parseAnd() {
        String e = parseEquality();
        while (match("AND") == 1) {
            e = "(" + e + " AND " + parseEquality() + ")";
        }
        return e;
    }

    String parseEquality() {
        String e = parseRelational();
        int cont = 1;
        while (cont == 1) {
            if (match("EQ") == 1) {
                e = "(" + e + " EQ " + parseRelational() + ")";
            } else if (match("NE") == 1) {
                e = "(" + e + " NE " + parseRelational() + ")";
            } else {
                cont = 0;
            }
        }
        return e;
    }

    String parseRelational() {
        String e = parseAdd();
        int cont = 1;
        while (cont == 1) {
            if (match("LT") == 1) {
                e = "(" + e + " LT " + parseAdd() + ")";
            } else if (match("LE") == 1) {
                e = "(" + e + " LE " + parseAdd() + ")";
            } else if (match("GT") == 1) {
                e = "(" + e + " GT " + parseAdd() + ")";
            } else if (match("GE") == 1) {
                e = "(" + e + " GE " + parseAdd() + ")";
            } else {
                cont = 0;
            }
        }
        return e;
    }

    String parseAdd() {
        String e = parseMul();
        int cont = 1;
        while (cont == 1) {
            if (match("PLUS") == 1) {
                e = "(" + e + " ADD " + parseMul() + ")";
            } else if (match("MINUS") == 1) {
                e = "(" + e + " SUB " + parseMul() + ")";
            } else {
                cont = 0;
            }
        }
        return e;
    }

    String parseMul() {
        String e = parseUnary();
        int cont = 1;
        while (cont == 1) {
            if (match("STAR") == 1) {
                e = "(" + e + " MUL " + parseUnary() + ")";
            } else if (match("SLASH") == 1) {
                e = "(" + e + " DIV " + parseUnary() + ")";
            } else if (match("PERCENT") == 1) {
                e = "(" + e + " MOD " + parseUnary() + ")";
            } else {
                cont = 0;
            }
        }
        return e;
    }

    String parseUnary() {
        if (match("NOT") == 1) {
            return "(NOT " + parseUnary() + ")";
        }
        if (match("MINUS") == 1) {
            return "(NEG " + parseUnary() + ")";
        }
        if (match("PLUS") == 1) {
            return "(PLUS " + parseUnary() + ")";
        }
        return parseCall();
    }

    String parseCall() {
        String e = parsePrimary();
        int cont = 1;
        while (cont == 1) {
            if (match("LPAREN") == 1) {
                ArrayList<String> args = new ArrayList<String>();
                if (check("RPAREN") == 0) {
                    args.add(parseExpression());
                    while (match("COMMA") == 1) {
                        args.add(parseExpression());
                    }
                }
                expect("RPAREN");
                if (e.equals("System.out.println")) {
                    String arg = "";
                    if (args.size() > 0) {
                        arg = args.get(0);
                    }
                    e = "println(" + arg + ")";
                } else {
                    e = e + "(";
                    int i = 0;
                    while (i < args.size()) {
                        if (i > 0) {
                            e = e + ", ";
                        }
                        e = e + args.get(i);
                        i = i + 1;
                    }
                    e = e + ")";
                }
            } else if (match("DOT") == 1) {
                e = e + "." + expectIdent();
            } else if (match("LBRACKET") == 1) {
                String idx = parseExpression();
                expect("RBRACKET");
                e = e + "[" + idx + "]";
            } else {
                cont = 0;
            }
        }
        return e;
    }

    String parsePrimary() {
        if (match("TRUE") == 1) {
            return "true";
        }
        if (match("FALSE") == 1) {
            return "false";
        }
        if (match("NULL") == 1) {
            return "null";
        }
        if (match("THIS") == 1) {
            return "this";
        }
        if (check("INT_LITERAL") == 1 || check("LONG_LITERAL") == 1 || check("FLOAT_LITERAL") == 1
                || check("DOUBLE_LITERAL") == 1) {
            String v = peek().lexeme;
            advance();
            return v;
        }
        if (check("STRING_LITERAL") == 1) {
            String v = peek().lexeme;
            advance();
            return v;
        }
        if (match("NEW") == 1) {
            String t = parseTypeName();
            if (match("LBRACKET") == 1) {
                String n = parseExpression();
                expect("RBRACKET");
                return "new " + t + "[" + n + "]";
            }
            expect("LPAREN");
            ArrayList<String> args = new ArrayList<String>();
            if (check("RPAREN") == 0) {
                args.add(parseExpression());
                while (match("COMMA") == 1) {
                    args.add(parseExpression());
                }
            }
            expect("RPAREN");
            String e = "new " + t + "(";
            int i = 0;
            while (i < args.size()) {
                if (i > 0) {
                    e = e + ", ";
                }
                e = e + args.get(i);
                i = i + 1;
            }
            return e + ")";
        }
        if (match("LPAREN") == 1) {
            String e = parseExpression();
            expect("RPAREN");
            return "(" + e + ")";
        }
        return expectIdentOrTypeKeyword();
    }

    void skipUntilGt() {
        int depth = 1;
        while (check("EOF") == 0 && depth > 0) {
            if (check("LT") == 1) {
                depth = depth + 1;
            } else if (check("GT") == 1) {
                depth = depth - 1;
            }
            advance();
        }
    }

    void lineIndent(int n) {
        int i = 0;
        while (i < n) {
            dump = dump + "  ";
            i = i + 1;
        }
    }

    void fail(String msg) {
        failed = 1;
        JnToken t = peek();
        dump = dump + "/* parse-error: " + msg + " at " + t.line + ":" + t.column + " */\n";
    }

    void synchronize() {
        while (check("EOF") == 0 && check("SEMICOLON") == 0 && check("RBRACE") == 0) {
            advance();
        }
        if (check("SEMICOLON") == 1) {
            advance();
        }
    }

    JnToken peek() {
        if (tokens == null || current >= tokens.size()) {
            return new JnToken("EOF", "", 0, 0);
        }
        JnToken t = tokens.get(current);
        if (t == null) {
            return new JnToken("EOF", "", 0, 0);
        }
        if (t.type == null) {
            t.type = "ERROR";
        }
        return t;
    }

    String peekNextType() {
        if (current + 1 >= tokens.size()) {
            return "EOF";
        }
        return tokens.get(current + 1).type;
    }

    int check(String type) {
        if (current >= tokens.size()) {
            return type.equals("EOF") ? 1 : 0;
        }
        return peek().type.equals(type) ? 1 : 0;
    }

    int match(String type) {
        if (check(type) == 1) {
            advance();
            return 1;
        }
        return 0;
    }

    void expect(String type) {
        if (match(type) == 0) {
            fail("expected " + type);
        }
    }

    String expectIdent() {
        if (check("IDENTIFIER") == 1) {
            String t = peek().lexeme;
            advance();
            return t;
        }
        fail("expected identifier");
        advance();
        return "?";
    }

    String expectIdentOrTypeKeyword() {
        if (check("IDENTIFIER") == 1 || check("INT") == 1 || check("LONG") == 1 || check("FLOAT") == 1
                || check("DOUBLE") == 1 || check("BOOLEAN") == 1 || check("VOID") == 1 || check("BYTE") == 1
                || check("SHORT") == 1 || check("CHAR") == 1) {
            String t = peek().lexeme;
            advance();
            return t;
        }
        fail("expected type or identifier");
        advance();
        return "?";
    }

    void advance() {
        if (current >= tokens.size()) {
            return;
        }
        if (peek().type.equals("EOF")) {
            return;
        }
        current = current + 1;
    }
}
