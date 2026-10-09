package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/** Ternary conditional {@code cond ? thenExpr : elseExpr} (right-associative).
 * @author 张晓达
 */
public record ConditionalExpression(
        Expression condition,
        Expression thenExpr,
        Expression elseExpr,
        SourceLocation location
) implements Expression {
}
