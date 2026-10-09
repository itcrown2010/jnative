package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/** Unlabeled {@code continue;} — targets innermost loop continue point.
 * @author 张晓达
 */
public record ContinueStatement(SourceLocation location) implements Statement {
}
