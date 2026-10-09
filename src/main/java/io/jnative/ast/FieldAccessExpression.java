package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * @author 张晓达
 */
public record FieldAccessExpression(
        Expression receiver,
        String fieldName,
        SourceLocation location
) implements Expression {
}
