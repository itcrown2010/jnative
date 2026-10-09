package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record Parameter(
        TypeName type,
        String name,
        boolean isArray,
        boolean isFinal,
        SourceLocation location
) implements AstNode {
    public Parameter(TypeName type, String name, boolean isArray, SourceLocation location) {
        this(type, name, isArray, false, location);
    }
}
