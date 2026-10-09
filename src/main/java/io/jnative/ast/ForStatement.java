package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * @author 张晓达
 */
public record ForStatement(
        Statement initializer,
        Expression condition,
        Expression update,
        Statement body,
        SourceLocation location
) implements Statement {
}
