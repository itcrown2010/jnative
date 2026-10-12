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
                char esc = advance();
                sb.append(switch (esc) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    case '0' -> '\0';
                    default -> esc;
                });
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

    private void number() {
        // First digit already consumed in scanToken. Detect 0x… from that digit.
        // Hex floats (0x1.0p0) and hex ints — hard error (language-support).
        if (source.charAt(start) == '0' && (peek() == 'x' || peek() == 'X')) {
            advance();
            while (isHexDigit(peek()) || peek() == '.' || peek() == 'p' || peek() == 'P'
                    || peek() == '+' || peek() == '-' || peek() == '_') {
                advance();
            }
            if (peek() == 'f' || peek() == 'F' || peek() == 'd' || peek() == 'D') {
                advance();
            }
            errorToken("hexadecimal numeric literals are not supported");
            return;
        }

        while (isDigit(peek())) {
            advance();
        }
        if (peek() == '_') {
            errorToken("numeric digit separators are not supported");
            return;
        }

        boolean isFloating = false;
        if (peek() == '.' && isDigit(peekNext())) {
            isFloating = true;
            advance();
            while (isDigit(peek())) {
                advance();
            }
            if (peek() == '_') {
                errorToken("numeric digit separators are not supported");
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
            while (isDigit(peek())) {
                advance();
            }
            if (peek() == '_') {
                errorToken("numeric digit separators are not supported");
                return;
            }
        }

        if (peek() == 'f' || peek() == 'F') {
            advance();
            addToken(TokenType.FLOAT_LITERAL);
            return;
        }
        if (peek() == 'd' || peek() == 'D') {
            advance();
            addToken(TokenType.DOUBLE_LITERAL);
            return;
        }
        if (!isFloating && (peek() == 'L' || peek() == 'l')) {
            advance();
            addToken(TokenType.LONG_LITERAL);
            return;
        }

        if (isFloating) {
            addToken(TokenType.DOUBLE_LITERAL);
        } else {
            addToken(TokenType.INT_LITERAL);
        }
    }

    private boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
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
