package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record ArrayAccessExpression(
        Expression array,
        Expression index,
        SourceLocation location
) implements Expression {
}
