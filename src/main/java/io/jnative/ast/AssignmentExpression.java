package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * Assignment to a variable or field (lvalue is an expression).
 */
public record AssignmentExpression(
        Expression target,
        Expression value,
        SourceLocation location
) implements Expression {
}
