package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record ForStatement(
        Statement initializer,
        Expression condition,
        Expression update,
        Statement body,
        SourceLocation location
) implements Statement {
}
