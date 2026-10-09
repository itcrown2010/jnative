package io.jnative.lexer;

import io.jnative.diagnostics.SourceLocation;

/**
 * @author 张晓达
 */
public record Token(TokenType type, String lexeme, int line, int column, String file) {

    public SourceLocation location() {
        return new SourceLocation(file, line, column);
    }

    @Override
    public String toString() {
        return type + "('" + lexeme + "')@" + line + ":" + column;
    }
}
