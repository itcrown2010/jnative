package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;

public record BlockStatement(List<Statement> statements, SourceLocation location) implements Statement {
}
