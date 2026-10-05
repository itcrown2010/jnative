package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record BinaryExpression(
        Expression left,
        BinaryOp operator,
        Expression right,
        SourceLocation location
) implements Expression {
}
