package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record GroupingExpression(Expression expression, SourceLocation location) implements Expression {
}
