/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;

/**
 * One-dimensional array initializer: {@code {1, 2, 3}} in a declaration or
 * {@code new int[] {1, 2, 3}}. {@code elementType} is always known to the parser (taken from the
 * declaration or from the {@code new} expression).
 *
 * @author 张晓达
 */
public record ArrayInitExpression(
        TypeName elementType,
        List<Expression> elements,
        SourceLocation location
) implements Expression {
}
