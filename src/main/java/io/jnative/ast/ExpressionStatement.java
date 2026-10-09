package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * @author 张晓达
 */
public record ExpressionStatement(Expression expression, SourceLocation location) implements Statement {
}
