package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;

/** One switch section: one or more labels followed by statements (fall-through allowed). */
public record SwitchSection(
        List<SwitchLabel> labels,
        List<Statement> statements,
        SourceLocation location
) {
}
