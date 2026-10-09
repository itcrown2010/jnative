package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * @author 张晓达
 */
public record IfStatement(
        Expression condition,
        Statement thenBranch,
        Statement elseBranch,
        SourceLocation location
) implements Statement {
}
