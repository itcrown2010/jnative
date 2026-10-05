package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record VariableDeclaration(
        TypeName type,
        boolean isArray,
        String name,
        Expression initializer,
        SourceLocation location
) implements Statement {
}
