package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record NewArrayExpression(
        TypeName elementType,
        Expression length,
        SourceLocation location
) implements Expression {
}
