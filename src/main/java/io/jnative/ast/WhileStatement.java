package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * @author 张晓达
 */
public record WhileStatement(
        Expression condition,
        Statement body,
        SourceLocation location
) implements Statement {
}
