package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record ReturnStatement(Expression value, SourceLocation location) implements Statement {
}
