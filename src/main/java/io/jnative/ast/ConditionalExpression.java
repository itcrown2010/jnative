package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/** Ternary conditional {@code cond ? thenExpr : elseExpr} (right-associative). */
public record ConditionalExpression(
        Expression condition,
        Expression thenExpr,
        Expression elseExpr,
        SourceLocation location
) implements Expression {
}
