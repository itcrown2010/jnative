package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record WhileStatement(
        Expression condition,
        Statement body,
        SourceLocation location
) implements Statement {
}
