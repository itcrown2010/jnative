package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record IfStatement(
        Expression condition,
        Statement thenBranch,
        Statement elseBranch,
        SourceLocation location
) implements Statement {
}
