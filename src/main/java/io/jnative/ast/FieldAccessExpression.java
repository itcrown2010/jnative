package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record FieldAccessExpression(
        Expression receiver,
        String fieldName,
        SourceLocation location
) implements Expression {
}
