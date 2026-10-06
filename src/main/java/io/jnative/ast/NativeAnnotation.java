package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.Optional;

/**
 * Minimal compiler-recognized {@code @Native} attributes (not a full annotation system).
 */
public record NativeAnnotation(
        Optional<String> symbol,
        Optional<String> library,
        SourceLocation location
) {
    public NativeAnnotation {
        symbol = symbol == null ? Optional.empty() : symbol;
        library = library == null ? Optional.empty() : library;
    }
}
