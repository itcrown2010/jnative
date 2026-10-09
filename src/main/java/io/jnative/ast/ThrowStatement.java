package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * @author 张晓达
 */
public record ThrowStatement(Expression expression, SourceLocation location) implements Statement {
}
