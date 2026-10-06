package io.jnative.lexer;

import java.util.HashMap;
import java.util.Map;

public enum TokenType {
    // Keywords
    PUBLIC,
    PRIVATE,
    PROTECTED,
    CLASS,
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
    FOR,
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
    THROW,
    THROWS,
    NATIVE,
    VOLATILE,

    // Identifiers & literals
    IDENTIFIER,
    INT_LITERAL,
    LONG_LITERAL,
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
    PLUS_PLUS,
    MINUS_MINUS,

    // Special
    EOF,
    ERROR;

    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();

    static {
        KEYWORDS.put("public", PUBLIC);
        KEYWORDS.put("private", PRIVATE);
        KEYWORDS.put("protected", PROTECTED);
        KEYWORDS.put("class", CLASS);
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
        KEYWORDS.put("for", FOR);
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
        KEYWORDS.put("throw", THROW);
        KEYWORDS.put("throws", THROWS);
        KEYWORDS.put("native", NATIVE);
        KEYWORDS.put("volatile", VOLATILE);
    }

    public static TokenType keywordOrIdentifier(String text) {
        return KEYWORDS.getOrDefault(text, IDENTIFIER);
    }
}
