package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.Optional;

/**
 * Compiler-recognized {@code @Native} attributes.
 * {@code effects} is a trusted contract string: PURE, NO_BLOCK, MAY_BLOCK, UNKNOWN (default).
 * @author 张晓达
 */
public record NativeAnnotation(
        Optional<String> symbol,
        Optional<String> library,
        Optional<String> effects,
        SourceLocation location
) {
    public NativeAnnotation {
        symbol = symbol == null ? Optional.empty() : symbol;
        library = library == null ? Optional.empty() : library;
        effects = effects == null ? Optional.empty() : effects;
    }

    public NativeAnnotation(Optional<String> symbol, Optional<String> library, SourceLocation location) {
        this(symbol, library, Optional.empty(), location);
    }
}
