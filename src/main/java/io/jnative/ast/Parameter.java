package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record Parameter(
        TypeName type,
        String name,
        boolean isArray,
        SourceLocation location
) implements AstNode {
}
