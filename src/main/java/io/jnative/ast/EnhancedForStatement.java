/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * Enhanced-for: {@code for (T elem : arrayOrIterable) body}.
 * Arrays lower to an index loop; Iterables use {@code iterator()}/{@code hasNext()}/{@code next()}.
 * @author 张晓达
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
