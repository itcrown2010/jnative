/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.lexer;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-written lexer for the JNative Java subset.
 * @author 张晓达
 */
public final class Lexer {
    private final String source;
    private final String file;
    private final List<Token> tokens = new ArrayList<>();
    private int start;
    private int current;
    private int line = 1;
    private int column = 1;
    private int startLine;
    private int startColumn;
    private boolean hasError;

    public Lexer(String source, String file) {
        this.source = source == null ? "" : source;
        this.file = file == null ? "<unknown>" : file;
    }

    public List<Token> tokenize() {
        tokens.clear();
        hasError = false;
        while (!isAtEnd()) {
            start = current;
            startLine = line;
            startColumn = column;
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "", line, column, file));
        return List.copyOf(tokens);
    }

    public boolean hasError() {
        return hasError;
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case '(' -> addToken(TokenType.LPAREN);
            case ')' -> addToken(TokenType.RPAREN);
            case '{' -> addToken(TokenType.LBRACE);
            case '}' -> addToken(TokenType.RBRACE);
            case '[' -> addToken(TokenType.LBRACKET);
            case ']' -> addToken(TokenType.RBRACKET);
            case ';' -> addToken(TokenType.SEMICOLON);
            case ',' -> addToken(TokenType.COMMA);
            case '.' -> addToken(TokenType.DOT);
            case '@' -> addToken(TokenType.AT);
            case '+' -> {
                if (match('+')) {
                    addToken(TokenType.PLUS_PLUS);
                } else {
                    addToken(TokenType.PLUS);
                }
            }
            case '-' -> {
                if (match('-')) {
                    addToken(TokenType.MINUS_MINUS);
                } else if (match('>')) {
                    addToken(TokenType.ARROW);
                } else {
                    addToken(TokenType.MINUS);
                }
            }
            case '*' -> addToken(TokenType.STAR);
            case '%' -> addToken(TokenType.PERCENT);
            case '!' -> addToken(match('=') ? TokenType.NE : TokenType.NOT);
            case '?' -> addToken(TokenType.QUESTION);
            case ':' -> {
                if (match(':')) {
                    addToken(TokenType.COLON_COLON);
                } else {
                    addToken(TokenType.COLON);
                }
            }
            case '=' -> addToken(match('=') ? TokenType.EQ : TokenType.ASSIGN);
            case '<' -> addToken(match('=') ? TokenType.LE : TokenType.LT);
            case '>' -> addToken(match('=') ? TokenType.GE : TokenType.GT);
            case '&' -> {
                if (match('&')) {
                    addToken(TokenType.AND);
                } else {
                    addToken(TokenType.BIT_AND);
                }
            }
            case '|' -> {
                if (match('|')) {
                    addToken(TokenType.OR);
                } else {
                    addToken(TokenType.BIT_OR);
                }
            }
            case '^' -> addToken(TokenType.BIT_XOR);
            case '~' -> addToken(TokenType.BIT_NOT);
            case '/' -> {
                if (match('/')) {
                    while (peek() != '\n' && !isAtEnd()) {
                        advance();
                    }
                } else if (match('*')) {
                    skipBlockComment();
                } else {
                    addToken(TokenType.SLASH);
                }
            }
            case '"' -> stringLiteral();
            case '\'' -> charLiteral();
            case ' ', '\r', '\t' -> {
                // ignore
            }
            case '\n' -> {
                // line/column already updated in advance()
            }
            default -> {
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    errorToken("unexpected character '" + c + "'");
                }
            }
        }
    }

    private void skipBlockComment() {
        while (!isAtEnd()) {
            if (peek() == '*' && peekNext() == '/') {
                advance();
                advance();
                return;
            }
            advance();
        }
        errorToken("unterminated block comment");
    }

    private void stringLiteral() {
        StringBuilder sb = new StringBuilder();
        while (peek() != '"' && !isAtEnd()) {
            char c = peek();
            if (c == '\n') {
                errorToken("unterminated string literal");
                return;
            }
            if (c == '\\') {
                advance();
                if (isAtEnd()) {
                    errorToken("unterminated string literal");
                    return;
                }
                int unit = escapeSequence();
                if (unit < 0) {
                    // Error recovery: drop the rest of the literal so the closing quote is not read as an opening one.
                    while (!isAtEnd() && peek() != '"' && peek() != '\n') {
                        if (peek() == '\\') {
                            advance();
                        }
                        if (!isAtEnd() && peek() != '\n') {
                            advance();
                        }
                    }
                    if (peek() == '"') {
                        advance();
                    }
                    return;
                }
                sb.append((char) unit);
            } else {
                sb.append(advance());
            }
        }
        if (isAtEnd()) {
            errorToken("unterminated string literal");
            return;
        }
        advance(); // closing "
        tokens.add(new Token(TokenType.STRING_LITERAL, sb.toString(), startLine, startColumn, file));
    }

    /**
     * Character literal: {@code 'a'}, {@code '\n'}, {@code '\u0041'}, {@code '\''}. The token lexeme is the
     * decimal UTF-16 code unit so the parser needs no escape handling of its own.
     */
    private void charLiteral() {
        if (isAtEnd() || peek() == '\n' || peek() == '\r') {
            errorToken("unterminated character literal");
            return;
        }
        char c = peek();
        if (c == '\'') {
            advance();
            errorToken("empty character literal");
            return;
        }
        int unit;
        if (c == '\\') {
            advance();
            unit = escapeSequence();
            if (unit < 0) {
                skipToCharLiteralEnd();
                return;
            }
        } else {
            advance();
            unit = c;
            if (Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) {
                skipToCharLiteralEnd();
                errorToken("character literal must be a single UTF-16 code unit (use a String for supplementary characters)");
                return;
            }
        }
        if (peek() != '\'') {
            skipToCharLiteralEnd();
            errorToken("unclosed character literal");
            return;
        }
        advance();
        tokens.add(new Token(TokenType.CHAR_LITERAL, Integer.toString(unit), startLine, startColumn, file));
    }

    /** Error recovery: drop the rest of a malformed character literal (up to its closing quote or end of line). */
    private void skipToCharLiteralEnd() {
        while (!isAtEnd() && peek() != '\n' && peek() != '\r' && peek() != '\'') {
            advance();
        }
        if (peek() == '\'') {
            advance();
        }
    }

    /**
     * Reads one escape sequence; the backslash is already consumed. Returns the UTF-16 code unit, or -1 after
     * emitting an error token. Supports {@code \n \t \r \b \f \s \" \' \\}, octal {@code \0..\377}
     * and {@code \}{@code uXXXX}.
     */
    private int escapeSequence() {
        if (isAtEnd()) {
            errorToken("unterminated escape sequence");
            return -1;
        }
        char esc = advance();
        switch (esc) {
            case 'n':
                return '\n';
            case 't':
                return '\t';
            case 'r':
                return '\r';
            case 'b':
                return '\b';
            case 'f':
                return '\f';
            case 's':
                return ' ';
            case '"':
                return '"';
            case '\'':
                return '\'';
            case '\\':
                return '\\';
            case 'u': {
                while (peek() == 'u') {
                    advance();
                }
                int v = 0;
                for (int i = 0; i < 4; i++) {
                    int d = Character.digit(peek(), 16);
                    if (d < 0) {
                        errorToken("illegal unicode escape");
                        return -1;
                    }
                    advance();
                    v = v * 16 + d;
                }
                return v;
            }
            default:
                if (esc >= '0' && esc <= '7') {
                    int v = esc - '0';
                    int max = esc <= '3' ? 2 : 1;
                    for (int i = 0; i < max && peek() >= '0' && peek() <= '7'; i++) {
                        v = v * 8 + (advance() - '0');
                    }
                    return v;
                }
                errorToken("illegal escape character '\\" + esc + "'");
                return -1;
        }
    }

    private void number() {
        // First digit already consumed in scanToken.
        if (source.charAt(start) == '0' && (peek() == 'x' || peek() == 'X')) {
            radixLiteral(16);
            return;
        }
        if (source.charAt(start) == '0' && (peek() == 'b' || peek() == 'B')) {
            radixLiteral(2);
            return;
        }

        if (!digitRun(10)) {
            return;
        }

        boolean isFloating = false;
        if (peek() == '.' && isDigit(peekNext())) {
            isFloating = true;
            advance();
            if (!digitRun(10)) {
                return;
            }
        }

        if (peek() == 'e' || peek() == 'E') {
            isFloating = true;
            advance();
            if (peek() == '+' || peek() == '-') {
                advance();
            }
            if (!isDigit(peek())) {
                errorToken("exponent has no digits");
                return;
            }
            if (!digitRun(10)) {
                return;
            }
        }

        TokenType type;
        if (peek() == 'f' || peek() == 'F') {
            advance();
            type = TokenType.FLOAT_LITERAL;
        } else if (peek() == 'd' || peek() == 'D') {
            advance();
            type = TokenType.DOUBLE_LITERAL;
        } else if (!isFloating && (peek() == 'L' || peek() == 'l')) {
            advance();
            type = TokenType.LONG_LITERAL;
        } else if (isFloating) {
            type = TokenType.DOUBLE_LITERAL;
        } else {
            type = TokenType.INT_LITERAL;
        }
        // Digit separators are only a lexical convenience: the parser sees plain digits.
        String text = source.substring(start, current).replace("_", "");
        tokens.add(new Token(type, text, startLine, startColumn, file));
    }

    /**
     * Hexadecimal ({@code 0xFF}) and binary ({@code 0b1010}) integer literals, with optional {@code L} suffix and
     * {@code _} separators. The token lexeme is the canonical signed decimal value (two's complement wrap, exactly
     * like javac: {@code 0xFFFFFFFF} is {@code -1}), so the parser and later phases need no radix knowledge.
     * Hexadecimal floating-point literals ({@code 0x1.0p0}) stay unsupported.
     */
    private void radixLiteral(int radix) {
        advance(); // x / b
        int digitsStart = current;
        if (peek() == '_') {
            skipRestOfNumber();
            errorToken("illegal underscore in numeric literal");
            return;
        }
        if (!digitRun(radix)) {
            return;
        }
        if (current == digitsStart) {
            skipRestOfNumber();
            errorToken(radix == 16 ? "hexadecimal literal has no digits" : "binary literal has no digits");
            return;
        }
        if (radix == 16 && (peek() == '.' || peek() == 'p' || peek() == 'P')) {
            skipRestOfNumber();
            errorToken("hexadecimal floating-point literals are not supported");
            return;
        }
        if (radix == 2 && isDigit(peek())) {
            skipRestOfNumber();
            errorToken("illegal digit in binary literal");
            return;
        }
        String digits = source.substring(digitsStart, current).replace("_", "");
        boolean isLong = false;
        if (peek() == 'L' || peek() == 'l') {
            advance();
            isLong = true;
        }
        java.math.BigInteger value = new java.math.BigInteger(digits, radix);
        if (isLong) {
            if (value.bitLength() > 64) {
                errorToken("long literal out of range");
                return;
            }
            tokens.add(new Token(TokenType.LONG_LITERAL, Long.toString(value.longValue()) + "L",
                    startLine, startColumn, file));
        } else {
            if (value.bitLength() > 32) {
                errorToken("int literal out of range");
                return;
            }
            tokens.add(new Token(TokenType.INT_LITERAL, Integer.toString(value.intValue()),
                    startLine, startColumn, file));
        }
    }

    /**
     * Consumes a run of digits of the given radix with Java's underscore rule: separators may only appear
     * <em>between</em> digits. Returns false (after emitting an error token) on a misplaced underscore.
     */
    private boolean digitRun(int radix) {
        while (true) {
            char c = peek();
            if (Character.digit(c, radix) >= 0 && c < 128) {
                advance();
            } else if (c == '_') {
                int k = current;
                while (k < source.length() && source.charAt(k) == '_') {
                    k++;
                }
                boolean digitAfter = k < source.length()
                        && source.charAt(k) < 128 && Character.digit(source.charAt(k), radix) >= 0;
                boolean digitBefore = current > start && isDigitOfAnyRadix(source.charAt(current - 1), radix);
                if (!digitAfter || !digitBefore) {
                    skipRestOfNumber();
                    errorToken("illegal underscore in numeric literal");
                    return false;
                }
                while (peek() == '_') {
                    advance();
                }
            } else {
                return true;
            }
        }
    }

    private static boolean isDigitOfAnyRadix(char c, int radix) {
        return c < 128 && Character.digit(c, radix) >= 0;
    }

    /** Error recovery: swallow the remainder of a malformed numeric literal so it yields a single error. */
    private void skipRestOfNumber() {
        while (isAlphaNumeric(peek()) || peek() == '.') {
            advance();
        }
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) {
            advance();
        }
        String text = source.substring(start, current);
        TokenType type = TokenType.keywordOrIdentifier(text);
        addToken(type);
    }

    private void addToken(TokenType type) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, startLine, startColumn, file));
    }

    private void errorToken(String message) {
        hasError = true;
        tokens.add(new Token(TokenType.ERROR, message, startLine, startColumn, file));
    }

    private boolean match(char expected) {
        if (isAtEnd() || source.charAt(current) != expected) {
            return false;
        }
        advance();
        return true;
    }

    private char advance() {
        char c = source.charAt(current++);
        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return c;
    }

    private char peek() {
        if (isAtEnd()) {
            return '\0';
        }
        return source.charAt(current);
    }

    private char peekNext() {
        if (current + 1 >= source.length()) {
            return '\0';
        }
        return source.charAt(current + 1);
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }
}
