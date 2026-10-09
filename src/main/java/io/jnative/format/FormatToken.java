package io.jnative.format;

/**
 * Token for source formatting (includes comments skipped by the compiler lexer).
 * @author 张晓达
 */
public record FormatToken(Kind kind, String text, int line1, int col1) {
    public enum Kind {
        IDENT,
        KEYWORD,
        LITERAL,
        STRING,
        PUNCT,
        LINE_COMMENT,
        BLOCK_COMMENT,
        NEWLINE,
        WHITESPACE,
        EOF,
        ERROR
    }
}
