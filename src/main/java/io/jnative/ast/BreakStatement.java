package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/** Unlabeled {@code break;} — targets innermost BreakTarget (loop today; switch later).
 * @author 张晓达
 */
public record BreakStatement(SourceLocation location) implements Statement {
}
