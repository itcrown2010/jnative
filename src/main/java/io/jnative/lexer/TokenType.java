/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.lexer;

import java.util.HashMap;
import java.util.Map;

/**
 * @author 张晓达
 */
public enum TokenType {
    // Keywords
    PUBLIC,
    PRIVATE,
    PROTECTED,
    CLASS,
    INTERFACE,
    ENUM,
    IMPLEMENTS,
    ABSTRACT,
    DEFAULT,
    SWITCH,
    CASE,
    STATIC,
    VOID,
    BYTE,
    SHORT,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    BOOLEAN,
    IF,
    ELSE,
    WHILE,
    DO,
    FOR,
    BREAK,
    CONTINUE,
    RETURN,
    TRUE,
    FALSE,
    EXTENDS,
    INSTANCEOF,
    NEW,
    THIS,
    SUPER,
    NULL,
    CHAR,
    TRY,
    CATCH,
    FINALLY,
    FINAL,
    THROW,
    THROWS,
    NATIVE,
    VOLATILE,
    ASYNC,
    AWAIT,
    IMPORT,
    PACKAGE,

    // Identifiers & literals
    IDENTIFIER,
    INT_LITERAL,
    LONG_LITERAL,
    /** Character literal; the lexeme is the decimal UTF-16 code unit (e.g. {@code 'a'} -> {@code "97"}). */
    CHAR_LITERAL,
    FLOAT_LITERAL,
    DOUBLE_LITERAL,
    STRING_LITERAL,

    // Symbols
    LBRACE,
    RBRACE,
    LPAREN,
    RPAREN,
    LBRACKET,
    RBRACKET,
    SEMICOLON,
    COMMA,
    DOT,
    AT,
    ASSIGN,
    PLUS,
    MINUS,
    STAR,
    SLASH,
    PERCENT,
    LT,
    GT,
    LE,
    GE,
    EQ,
    NE,
    AND,
    OR,
    NOT,
    BIT_AND,
    BIT_OR,
    BIT_XOR,
    BIT_NOT,
    QUESTION,
    COLON,
    PLUS_PLUS,
    MINUS_MINUS,
    ARROW,
    COLON_COLON,

    // Special
    EOF,
    ERROR;

    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();

    static {
        KEYWORDS.put("public", PUBLIC);
        KEYWORDS.put("private", PRIVATE);
        KEYWORDS.put("protected", PROTECTED);
        KEYWORDS.put("class", CLASS);
        KEYWORDS.put("interface", INTERFACE);
        KEYWORDS.put("enum", ENUM);
        KEYWORDS.put("implements", IMPLEMENTS);
        KEYWORDS.put("abstract", ABSTRACT);
        KEYWORDS.put("default", DEFAULT);
        KEYWORDS.put("switch", SWITCH);
        KEYWORDS.put("case", CASE);
        KEYWORDS.put("static", STATIC);
        KEYWORDS.put("void", VOID);
        KEYWORDS.put("byte", BYTE);
        KEYWORDS.put("short", SHORT);
        KEYWORDS.put("int", INT);
        KEYWORDS.put("long", LONG);
        KEYWORDS.put("float", FLOAT);
        KEYWORDS.put("double", DOUBLE);
        KEYWORDS.put("boolean", BOOLEAN);
        KEYWORDS.put("if", IF);
        KEYWORDS.put("else", ELSE);
        KEYWORDS.put("while", WHILE);
        KEYWORDS.put("do", DO);
        KEYWORDS.put("for", FOR);
        KEYWORDS.put("break", BREAK);
        KEYWORDS.put("continue", CONTINUE);
        KEYWORDS.put("return", RETURN);
        KEYWORDS.put("true", TRUE);
        KEYWORDS.put("false", FALSE);
        KEYWORDS.put("extends", EXTENDS);
        KEYWORDS.put("instanceof", INSTANCEOF);
        KEYWORDS.put("new", NEW);
        KEYWORDS.put("this", THIS);
        KEYWORDS.put("super", SUPER);
        KEYWORDS.put("null", NULL);
        KEYWORDS.put("char", CHAR);
        KEYWORDS.put("try", TRY);
        KEYWORDS.put("catch", CATCH);
        KEYWORDS.put("finally", FINALLY);
        KEYWORDS.put("final", FINAL);
        KEYWORDS.put("throw", THROW);
        KEYWORDS.put("throws", THROWS);
        KEYWORDS.put("native", NATIVE);
        KEYWORDS.put("volatile", VOLATILE);
        KEYWORDS.put("async", ASYNC);
        KEYWORDS.put("await", AWAIT);
        KEYWORDS.put("import", IMPORT);
        KEYWORDS.put("package", PACKAGE);
    }

    public static TokenType keywordOrIdentifier(String text) {
        return KEYWORDS.getOrDefault(text, IDENTIFIER);
    }
}
