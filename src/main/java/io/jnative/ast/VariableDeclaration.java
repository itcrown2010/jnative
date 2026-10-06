package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record VariableDeclaration(
        TypeName type,
        boolean isArray,
        String name,
        Expression initializer,
        boolean stackAllocated,
        SourceLocation location
) implements Statement {
    public VariableDeclaration(
            TypeName type,
            boolean isArray,
            String name,
            Expression initializer,
            SourceLocation location
    ) {
        this(type, isArray, name, initializer, false, location);
    }
}
