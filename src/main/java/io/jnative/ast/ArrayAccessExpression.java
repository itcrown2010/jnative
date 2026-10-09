package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * @author 张晓达
 */
public record ArrayAccessExpression(
        Expression array,
        Expression index,
        SourceLocation location
) implements Expression {
}
