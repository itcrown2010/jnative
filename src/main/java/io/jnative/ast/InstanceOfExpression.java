package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record InstanceOfExpression(
        Expression expression,
        TypeName type,
        SourceLocation location
) implements Expression {
}
