/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.format;

import io.jnative.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;

/**
 * Lexer that preserves comments and exact literal text for formatting.
 * @author 张晓达
 */
public final class FormatLexer {
    private final String source;
    private int start;
    private int current;
    private int line = 1;
    private int column = 1;
    private int startLine;
    private int startColumn;
    private boolean error;

    public FormatLexer(String source) {
        this.source = source == null ? "" : source;
    }

    public boolean hasError() {
        return error;
    }

    public List<FormatToken> tokenize() {
        List<FormatToken> out = new ArrayList<>();
        error = false;
        while (!isAtEnd()) {
            start = current;
            startLine = line;
            startColumn = column;
            scan(out);
        }
        out.add(new FormatToken(FormatToken.Kind.EOF, "", line, column));
        return out;
    }

    private void scan(List<FormatToken> out) {
        char c = advance();
        switch (c) {
            case '(' -> punct(out, "(");
            case ')' -> punct(out, ")");
            case '{' -> punct(out, "{");
            case '}' -> punct(out, "}");
            case '[' -> punct(out, "[");
            case ']' -> punct(out, "]");
            case ';' -> punct(out, ";");
            case ',' -> punct(out, ",");
            case '.' -> punct(out, ".");
            case '@' -> punct(out, "@");
            case '?' -> punct(out, "?");
            case ':' -> {
                if (match(':')) {
                    punct(out, "::");
                } else {
                    punct(out, ":");
                }
            }
            case '+' -> {
                if (match('+')) {
                    punct(out, "++");
                } else {
                    punct(out, "+");
                }
            }
            case '-' -> {
                if (match('-')) {
                    punct(out, "--");
                } else if (match('>')) {
                    punct(out, "->");
                } else {
                    punct(out, "-");
                }
            }
            case '*' -> punct(out, "*");
            case '%' -> punct(out, "%");
            case '^' -> punct(out, "^");
            case '~' -> punct(out, "~");
            case '!' -> punct(out, match('=') ? "!=" : "!");
            case '=' -> punct(out, match('=') ? "==" : "=");
            case '<' -> punct(out, match('=') ? "<=" : "<");
            case '>' -> punct(out, match('=') ? ">=" : ">");
            case '&' -> punct(out, match('&') ? "&&" : "&");
            case '|' -> punct(out, match('|') ? "||" : "|");
            case '/' -> {
                if (match('/')) {
                    while (peek() != '\n' && !isAtEnd()) {
                        advance();
                    }
                    out.add(tok(FormatToken.Kind.LINE_COMMENT, text()));
                } else if (match('*')) {
                    while (!isAtEnd()) {
                        if (peek() == '*' && peekNext() == '/') {
                            advance();
                            advance();
                            out.add(tok(FormatToken.Kind.BLOCK_COMMENT, text()));
                            return;
                        }
                        advance();
                    }
                    error = true;
                    out.add(tok(FormatToken.Kind.ERROR, "unterminated block comment"));
                } else {
                    punct(out, "/");
                }
            }
            case '"' -> {
                while (peek() != '"' && !isAtEnd()) {
                    if (peek() == '\\') {
                        advance();
                        if (!isAtEnd()) {
                            advance();
                        }
                    } else {
                        advance();
                    }
                }
                if (isAtEnd()) {
                    error = true;
                    out.add(tok(FormatToken.Kind.ERROR, "unterminated string"));
                    return;
                }
                advance(); // closing "
                out.add(tok(FormatToken.Kind.STRING, text()));
            }
            case ' ', '\t', '\r' -> {
                while (peek() == ' ' || peek() == '\t' || peek() == '\r') {
                    advance();
                }
                out.add(tok(FormatToken.Kind.WHITESPACE, text()));
            }
            case '\n' -> out.add(tok(FormatToken.Kind.NEWLINE, "\n"));
            default -> {
                if (isDigit(c)) {
                    while (isDigit(peek()) || peek() == '.' || peek() == 'e' || peek() == 'E'
                            || peek() == '+' || peek() == '-' || peek() == 'f' || peek() == 'F'
                            || peek() == 'd' || peek() == 'D' || peek() == 'L' || peek() == 'l'
                            || peek() == 'x' || peek() == 'X' || isHex(peek())) {
                        // stop if +/− not part of exponent
                        char p = peek();
                        if ((p == '+' || p == '-') && !(source.charAt(current - 1) == 'e'
                                || source.charAt(current - 1) == 'E')) {
                            break;
                        }
                        advance();
                    }
                    out.add(tok(FormatToken.Kind.LITERAL, text()));
                } else if (isAlpha(c)) {
                    while (isAlphaNum(peek())) {
                        advance();
                    }
                    String t = text();
                    TokenType kw = TokenType.keywordOrIdentifier(t);
                    out.add(tok(kw == TokenType.IDENTIFIER ? FormatToken.Kind.IDENT : FormatToken.Kind.KEYWORD, t));
                } else {
                    error = true;
                    out.add(tok(FormatToken.Kind.ERROR, "unexpected '" + c + "'"));
                }
            }
        }
    }

    private void punct(List<FormatToken> out, String s) {
        out.add(tok(FormatToken.Kind.PUNCT, s));
    }

    private FormatToken tok(FormatToken.Kind k, String t) {
        return new FormatToken(k, t, startLine, startColumn);
    }

    private String text() {
        return source.substring(start, current);
    }

    private boolean match(char e) {
        if (isAtEnd() || source.charAt(current) != e) {
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
        return isAtEnd() ? '\0' : source.charAt(current);
    }

    private char peekNext() {
        return current + 1 >= source.length() ? '\0' : source.charAt(current + 1);
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isHex(char c) {
        return isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isAlphaNum(char c) {
        return isAlpha(c) || isDigit(c);
    }
}
