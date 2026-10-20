/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * @author 张晓达
 */
public record InstanceOfExpression(
        Expression expression,
        TypeName type,
        SourceLocation location,
        String binding
) implements Expression {
    public InstanceOfExpression(Expression expression, TypeName type, SourceLocation location) {
        this(expression, type, location, null);
    }
}
