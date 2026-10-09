package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * @author 张晓达
 */
public record GroupingExpression(Expression expression, SourceLocation location) implements Expression {
}
