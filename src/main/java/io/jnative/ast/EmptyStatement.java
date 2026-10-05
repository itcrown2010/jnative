package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record EmptyStatement(SourceLocation location) implements Statement {
}
