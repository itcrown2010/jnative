package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record DoWhileStatement(
        Statement body,
        Expression condition,
        SourceLocation location
) implements Statement {
}
