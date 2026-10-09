package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * Assignment to a variable or field (lvalue is an expression).
 * @author 张晓达
 */
public record AssignmentExpression(
        Expression target,
        Expression value,
        SourceLocation location
) implements Expression {
}
