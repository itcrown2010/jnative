package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * Enhanced-for: {@code for (T elem : arrayOrIterable) body}.
 * Arrays lower to an index loop; Iterables use {@code iterator()}/{@code hasNext()}/{@code next()}.
 */
public record EnhancedForStatement(
        TypeName elementType,
        boolean elementIsArray,
        boolean elementIsFinal,
        String variableName,
        Expression array,
        Statement body,
        SourceLocation location
) implements Statement {
}
