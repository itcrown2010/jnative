package io.jnative.lexer;

import io.jnative.diagnostics.SourceLocation;

public record Token(TokenType type, String lexeme, int line, int column, String file) {

    public SourceLocation location() {
        return new SourceLocation(file, line, column);
    }

    @Override
    public String toString() {
        return type + "('" + lexeme + "')@" + line + ":" + column;
    }
}
