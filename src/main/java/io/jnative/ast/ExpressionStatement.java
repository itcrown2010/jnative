package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record ExpressionStatement(Expression expression, SourceLocation location) implements Statement {
}
