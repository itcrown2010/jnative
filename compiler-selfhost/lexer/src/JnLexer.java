/**
 * Self-hosted JNative lexer — mirrors io.jnative.lexer.Lexer token stream.
 * Compatibility verified via scripts/selfhost-lex-compare.sh
 * @author Zhang Xiaoda
 */
class JnToken {
    String type;
    String lexeme;
    int line;
    int column;

    JnToken(String t, String lex, int ln, int col) {
        type = t;
        lexeme = lex;
        line = ln;
        column = col;
    }
}

class JnLexer {
    String source;
    String file;
    ArrayList<JnToken> tokens;
    int start;
    int current;
    int line;
    int column;
    int startLine;
    int startColumn;
    int hasError;
    HashMap<String, String> keywords;

    JnLexer(String src, String f) {
        if (src == null) {
            source = "";
        } else {
            source = src;
        }
        if (f == null) {
            file = "unknown";
        } else {
            file = f;
        }
        tokens = new ArrayList<JnToken>();
        start = 0;
        current = 0;
        line = 1;
        column = 1;
        startLine = 1;
        startColumn = 1;
        hasError = 0;
        keywords = new HashMap<String, String>();
        installKeywords();
    }

    void installKeywords() {
        keywords.put("public", "PUBLIC");
        keywords.put("private", "PRIVATE");
        keywords.put("protected", "PROTECTED");
        keywords.put("class", "CLASS");
        keywords.put("interface", "INTERFACE");
        keywords.put("enum", "ENUM");
        keywords.put("implements", "IMPLEMENTS");
        keywords.put("abstract", "ABSTRACT");
        keywords.put("default", "DEFAULT");
        keywords.put("switch", "SWITCH");
        keywords.put("case", "CASE");
        keywords.put("static", "STATIC");
        keywords.put("void", "VOID");
        keywords.put("byte", "BYTE");
        keywords.put("short", "SHORT");
        keywords.put("int", "INT");
        keywords.put("long", "LONG");
        keywords.put("float", "FLOAT");
        keywords.put("double", "DOUBLE");
        keywords.put("boolean", "BOOLEAN");
        keywords.put("if", "IF");
        keywords.put("else", "ELSE");
        keywords.put("while", "WHILE");
        keywords.put("do", "DO");
        keywords.put("for", "FOR");
        keywords.put("break", "BREAK");
        keywords.put("continue", "CONTINUE");
        keywords.put("return", "RETURN");
        keywords.put("true", "TRUE");
        keywords.put("false", "FALSE");
        keywords.put("extends", "EXTENDS");
        keywords.put("instanceof", "INSTANCEOF");
        keywords.put("new", "NEW");
        keywords.put("this", "THIS");
        keywords.put("super", "SUPER");
        keywords.put("null", "NULL");
        keywords.put("char", "CHAR");
        keywords.put("try", "TRY");
        keywords.put("catch", "CATCH");
        keywords.put("finally", "FINALLY");
        keywords.put("final", "FINAL");
        keywords.put("throw", "THROW");
        keywords.put("throws", "THROWS");
        keywords.put("native", "NATIVE");
        keywords.put("volatile", "VOLATILE");
        keywords.put("async", "ASYNC");
        keywords.put("await", "AWAIT");
        keywords.put("import", "IMPORT");
        keywords.put("package", "PACKAGE");
    }

    ArrayList<JnToken> tokenize() {
        tokens = new ArrayList<JnToken>();
        hasError = 0;
        start = 0;
        current = 0;
        line = 1;
        column = 1;
        while (!isAtEnd()) {
            start = current;
            startLine = line;
            startColumn = column;
            scanToken();
        }
        tokens.add(new JnToken("EOF", "", line, column));
        return tokens;
    }

    void scanToken() {
        int c = advance();
        if (c == 40) {
            addToken("LPAREN");
        } else if (c == 41) {
            addToken("RPAREN");
        } else if (c == 123) {
            addToken("LBRACE");
        } else if (c == 125) {
            addToken("RBRACE");
        } else if (c == 91) {
            addToken("LBRACKET");
        } else if (c == 93) {
            addToken("RBRACKET");
        } else if (c == 59) {
            addToken("SEMICOLON");
        } else if (c == 44) {
            addToken("COMMA");
        } else if (c == 46) {
            addToken("DOT");
        } else if (c == 64) {
            addToken("AT");
        } else if (c == 43) {
            if (match(43)) {
                addToken("PLUS_PLUS");
            } else {
                addToken("PLUS");
            }
        } else if (c == 45) {
            if (match(45)) {
                addToken("MINUS_MINUS");
            } else if (match(62)) {
                addToken("ARROW");
            } else {
                addToken("MINUS");
            }
        } else if (c == 42) {
            addToken("STAR");
        } else if (c == 37) {
            addToken("PERCENT");
        } else if (c == 33) {
            if (match(61)) {
                addToken("NE");
            } else {
                addToken("NOT");
            }
        } else if (c == 63) {
            addToken("QUESTION");
        } else if (c == 58) {
            if (match(58)) {
                addToken("COLON_COLON");
            } else {
                addToken("COLON");
            }
        } else if (c == 61) {
            if (match(61)) {
                addToken("EQ");
            } else {
                addToken("ASSIGN");
            }
        } else if (c == 60) {
            if (match(61)) {
                addToken("LE");
            } else {
                addToken("LT");
            }
        } else if (c == 62) {
            if (match(61)) {
                addToken("GE");
            } else {
                addToken("GT");
            }
        } else if (c == 38) {
            if (match(38)) {
                addToken("AND");
            } else {
                addToken("BIT_AND");
            }
        } else if (c == 124) {
            if (match(124)) {
                addToken("OR");
            } else {
                addToken("BIT_OR");
            }
        } else if (c == 94) {
            addToken("BIT_XOR");
        } else if (c == 126) {
            addToken("BIT_NOT");
        } else if (c == 47) {
            if (match(47)) {
                while (peek() != 10 && !isAtEnd()) {
                    advance();
                }
            } else if (match(42)) {
                skipBlockComment();
            } else {
                addToken("SLASH");
            }
        } else if (c == 34) {
            stringLiteral();
        } else if (c == 32 || c == 13 || c == 9) {
            /* skip */
        } else if (c == 10) {
            /* line updated in advance */
        } else if (isDigit(c)) {
            number();
        } else if (isAlpha(c)) {
            identifier();
        } else {
            errorToken("unexpected character '" + charToString(c) + "'");
        }
    }

    void skipBlockComment() {
        while (!isAtEnd()) {
            if (peek() == 42 && peekNext() == 47) {
                advance();
                advance();
                return;
            }
            advance();
        }
        errorToken("unterminated block comment");
    }

    void stringLiteral() {
        String sb = "";
        while (peek() != 34 && !isAtEnd()) {
            int c = peek();
            if (c == 10) {
                errorToken("unterminated string literal");
                return;
            }
            if (c == 92) {
                advance();
                if (isAtEnd()) {
                    errorToken("unterminated string literal");
                    return;
                }
                int esc = advance();
                if (esc == 110) {
                    sb = sb + "\n";
                } else if (esc == 116) {
                    sb = sb + "\t";
                } else if (esc == 114) {
                    sb = sb + "\r";
                } else if (esc == 34) {
                    sb = sb + "\"";
                } else if (esc == 92) {
                    sb = sb + "\\";
                } else if (esc == 48) {
                    sb = sb + "\0";
                } else {
                    sb = sb + charToString(esc);
                }
            } else {
                sb = sb + charToString(advance());
            }
        }
        if (isAtEnd()) {
            errorToken("unterminated string literal");
            return;
        }
        advance();
        tokens.add(new JnToken("STRING_LITERAL", sb, startLine, startColumn));
    }

    void number() {
        if (source.charAt(start) == 48 && (peek() == 120 || peek() == 88)) {
            advance();
            while (isHexDigit(peek()) || peek() == 46 || peek() == 112 || peek() == 80
                    || peek() == 43 || peek() == 45 || peek() == 95) {
                advance();
            }
            if (peek() == 102 || peek() == 70 || peek() == 100 || peek() == 68) {
                advance();
            }
            errorToken("hexadecimal numeric literals are not supported");
            return;
        }
        while (isDigit(peek())) {
            advance();
        }
        if (peek() == 95) {
            errorToken("numeric digit separators are not supported");
            return;
        }
        int isFloating = 0;
        if (peek() == 46 && isDigit(peekNext())) {
            isFloating = 1;
            advance();
            while (isDigit(peek())) {
                advance();
            }
            if (peek() == 95) {
                errorToken("numeric digit separators are not supported");
                return;
            }
        }
        if (peek() == 101 || peek() == 69) {
            isFloating = 1;
            advance();
            if (peek() == 43 || peek() == 45) {
                advance();
            }
            if (!isDigit(peek())) {
                errorToken("exponent has no digits");
                return;
            }
            while (isDigit(peek())) {
                advance();
            }
            if (peek() == 95) {
                errorToken("numeric digit separators are not supported");
                return;
            }
        }
        if (peek() == 102 || peek() == 70) {
            advance();
            addToken("FLOAT_LITERAL");
            return;
        }
        if (peek() == 100 || peek() == 68) {
            advance();
            addToken("DOUBLE_LITERAL");
            return;
        }
        if (isFloating == 0 && (peek() == 76 || peek() == 108)) {
            advance();
            addToken("LONG_LITERAL");
            return;
        }
        if (isFloating == 1) {
            addToken("DOUBLE_LITERAL");
        } else {
            addToken("INT_LITERAL");
        }
    }

    void identifier() {
        while (isAlphaNumeric(peek())) {
            advance();
        }
        String text = source.substring(start, current);
        String type = keywords.get(text);
        if (type == null) {
            type = "IDENTIFIER";
        }
        addTokenKind(type, text);
    }

    void addToken(String type) {
        String text = source.substring(start, current);
        addTokenKind(type, text);
    }

    void addTokenKind(String type, String text) {
        tokens.add(new JnToken(type, text, startLine, startColumn));
    }

    void errorToken(String message) {
        hasError = 1;
        tokens.add(new JnToken("ERROR", message, startLine, startColumn));
    }

    boolean match(int expected) {
        if (isAtEnd() || source.charAt(current) != expected) {
            return false;
        }
        advance();
        return true;
    }

    int advance() {
        int c = source.charAt(current);
        current = current + 1;
        if (c == 10) {
            line = line + 1;
            column = 1;
        } else {
            column = column + 1;
        }
        return c;
    }

    int peek() {
        if (isAtEnd()) {
            return 0;
        }
        return source.charAt(current);
    }

    int peekNext() {
        if (current + 1 >= source.length()) {
            return 0;
        }
        return source.charAt(current + 1);
    }

    boolean isAtEnd() {
        return current >= source.length();
    }

    boolean isDigit(int c) {
        return c >= 48 && c <= 57;
    }

    boolean isHexDigit(int c) {
        return (c >= 48 && c <= 57) || (c >= 97 && c <= 102) || (c >= 65 && c <= 70);
    }

    boolean isAlpha(int c) {
        return (c >= 97 && c <= 122) || (c >= 65 && c <= 90) || c == 95;
    }

    boolean isAlphaNumeric(int c) {
        return isAlpha(c) || isDigit(c);
    }

    String charToString(int c) {
        /* Build one-char string via String.from if available; fallback tiny array */
        if (c == 10) {
            return "\n";
        }
        if (c == 9) {
            return "\t";
        }
        if (c == 13) {
            return "\r";
        }
        if (c == 0) {
            return "\0";
        }
        byte[] b = new byte[1];
        b[0] = (byte) c;
        return String.fromBytes(b);
    }
}
