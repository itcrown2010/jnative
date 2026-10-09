package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * @author 张晓达
 */
public record DoWhileStatement(
        Statement body,
        Expression condition,
        SourceLocation location
) implements Statement {
}
