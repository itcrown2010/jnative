/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.lexer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class LexerTest {

    @Test
    void keywordsAndIdentifiers() {
        List<Token> tokens = new Lexer("public class Hello static void main", "t.java").tokenize();
        assertEquals(TokenType.PUBLIC, tokens.get(0).type());
        assertEquals(TokenType.CLASS, tokens.get(1).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(2).type());
        assertEquals("Hello", tokens.get(2).lexeme());
        assertEquals(TokenType.STATIC, tokens.get(3).type());
        assertEquals(TokenType.VOID, tokens.get(4).type());
    }

    @Test
    void operatorsAndPunctuation() {
        List<Token> tokens = new Lexer("<= >= == != && || ++ --", "t.java").tokenize();
        assertEquals(TokenType.LE, tokens.get(0).type());
        assertEquals(TokenType.GE, tokens.get(1).type());
        assertEquals(TokenType.EQ, tokens.get(2).type());
        assertEquals(TokenType.NE, tokens.get(3).type());
        assertEquals(TokenType.AND, tokens.get(4).type());
        assertEquals(TokenType.OR, tokens.get(5).type());
        assertEquals(TokenType.PLUS_PLUS, tokens.get(6).type());
        assertEquals(TokenType.MINUS_MINUS, tokens.get(7).type());
    }

    @Test
    void literalsAndComments() {
        String src = """
                // line comment
                int x = 10;
                long y = 20L;
                double z = 1.5;
                boolean ok = true;
                /* block
                   comment */
                String s = "hi\\n";
                """;
        List<Token> tokens = new Lexer(src, "t.java").tokenize();
        assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.INT_LITERAL && t.lexeme().equals("10")));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.LONG_LITERAL));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.DOUBLE_LITERAL && t.lexeme().equals("1.5")));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.TRUE));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.STRING_LITERAL && t.lexeme().equals("hi\n")));
        assertFalse(tokens.stream().anyMatch(t -> t.type() == TokenType.ERROR));
    }

    private static String lex1(String src, TokenType expected) {
        List<Token> tokens = new Lexer(src, "t.java").tokenize();
        assertEquals(expected, tokens.get(0).type(), src);
        assertEquals(TokenType.EOF, tokens.get(1).type(), src);
        return tokens.get(0).lexeme();
    }

    @Test
    void charLiterals() {
        // lexeme is the decimal UTF-16 code unit
        assertEquals("97", lex1("'a'", TokenType.CHAR_LITERAL));
        assertEquals("10", lex1("'\\n'", TokenType.CHAR_LITERAL));
        assertEquals("39", lex1("'\\''", TokenType.CHAR_LITERAL));
        assertEquals("92", lex1("'\\\\'", TokenType.CHAR_LITERAL));
        assertEquals("65", lex1("'\\u0041'", TokenType.CHAR_LITERAL));
        assertEquals("65", lex1("'\\101'", TokenType.CHAR_LITERAL));
        assertEquals("0", lex1("'\\0'", TokenType.CHAR_LITERAL));
        assertEquals("20013", lex1("'\u4e2d'", TokenType.CHAR_LITERAL));
    }

    @Test
    void malformedCharLiteralsAreErrors() {
        for (String s : new String[] {"''", "'ab'", "'a", "'\\q'", "'\\u12'"}) {
            List<Token> tokens = new Lexer(s, "t.java").tokenize();
            assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.ERROR), s);
        }
    }

    @Test
    void radixAndSeparatedIntegerLiterals() {
        // radix literals are canonicalised to the signed decimal value (two's complement, like javac)
        assertEquals("255", lex1("0xFF", TokenType.INT_LITERAL));
        assertEquals("255", lex1("0Xff", TokenType.INT_LITERAL));
        assertEquals("-1", lex1("0xFFFFFFFF", TokenType.INT_LITERAL));
        assertEquals("-2147483648", lex1("0x80000000", TokenType.INT_LITERAL));
        assertEquals("10", lex1("0b1010", TokenType.INT_LITERAL));
        assertEquals("-889275714", lex1("0xCAFE_BABE", TokenType.INT_LITERAL));
        assertEquals("1000000", lex1("1_000_000", TokenType.INT_LITERAL));
        assertEquals("4294967295L", lex1("0xFFFFFFFFL", TokenType.LONG_LITERAL));
        assertEquals("-1L", lex1("0xFFFFFFFFFFFFFFFFL", TokenType.LONG_LITERAL));
        assertEquals("123456789012L", lex1("123_456_789_012L", TokenType.LONG_LITERAL));
        assertEquals("1_0.2_5".replace("_", ""), lex1("1_0.2_5", TokenType.DOUBLE_LITERAL));
    }

    @Test
    void malformedNumbersAreErrors() {
        for (String s : new String[] {"1_", "0x_1", "0x", "0b102", "0x1FFFFFFFF", "0x1.8p3", "1__"}) {
            List<Token> tokens = new Lexer(s, "t.java").tokenize();
            assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.ERROR), s);
        }
    }

    @Test
    void recordsSourcePosition() {
        Token token = new Lexer("\n  abc", "pos.java").tokenize().get(0);
        assertEquals(TokenType.IDENTIFIER, token.type());
        assertEquals(2, token.line());
        assertEquals(3, token.column());
        assertEquals("pos.java", token.file());
    }
}
