/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;

/**
 * @author 张晓达
 */
public record LambdaExpression(
        List<LambdaParameter> parameters,
        Expression expressionBody,
        BlockStatement blockBody,
        SourceLocation location
) implements Expression {
    public boolean isBlock() {
        return blockBody != null;
    }
}
