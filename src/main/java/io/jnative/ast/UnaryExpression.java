package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record UnaryExpression(
        UnaryOp operator,
        Expression operand,
        SourceLocation location
) implements Expression {
}
