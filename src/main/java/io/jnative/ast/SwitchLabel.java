package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/** {@code case <expr>:} or {@code default:}. */
public sealed interface SwitchLabel permits CaseLabel, DefaultLabel {
    SourceLocation location();
}
