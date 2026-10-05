package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record CastExpression(
        TypeName type,
        Expression expression,
        SourceLocation location
) implements Expression {
}
