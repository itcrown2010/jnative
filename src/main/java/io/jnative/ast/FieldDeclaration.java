package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record FieldDeclaration(
        TypeName type,
        String name,
        SourceLocation location
) implements AstNode {
}
