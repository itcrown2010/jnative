package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;

public record LambdaExpression(
        List<LambdaParameter> parameters,
        Expression expressionBody,
        BlockStatement blockBody,
        SourceLocation location
) implements Expression {
    public boolean isBlock() {
        return blockBody != null;
    }
}
