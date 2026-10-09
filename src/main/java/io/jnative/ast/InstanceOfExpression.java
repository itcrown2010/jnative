package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * @author 张晓达
 */
public record InstanceOfExpression(
        Expression expression,
        TypeName type,
        SourceLocation location
) implements Expression {
}
