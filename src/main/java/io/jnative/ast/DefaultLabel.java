package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

public record DefaultLabel(SourceLocation location) implements SwitchLabel {
}
