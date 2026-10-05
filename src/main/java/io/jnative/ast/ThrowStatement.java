package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record ThrowStatement(Expression expression, SourceLocation location) implements Statement {
}
