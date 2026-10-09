package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;

/** Classic {@code switch} statement (not switch expression / arrow syntax). */
public record SwitchStatement(
        Expression selector,
        List<SwitchSection> sections,
        SourceLocation location
) implements Statement {
}
