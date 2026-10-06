package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record FieldDeclaration(
        TypeName type,
        boolean isArray,
        String name,
        boolean isStatic,
        boolean isVolatile,
        Expression initializer,
        SourceLocation location
) implements AstNode {
}
