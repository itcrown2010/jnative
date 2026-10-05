package io.jnative.semantic;

import io.jnative.diagnostics.SourceLocation;

public record ParameterSymbol(String name, JType type, SourceLocation location) {
}
