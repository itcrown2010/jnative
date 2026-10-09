package io.jnative.semantic;

import io.jnative.diagnostics.SourceLocation;

/**
 * @author 张晓达
 */
public record ParameterSymbol(String name, JType type, SourceLocation location, boolean isFinal) {
    public ParameterSymbol(String name, JType type, SourceLocation location) {
        this(name, type, location, false);
    }
}
