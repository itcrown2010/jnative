/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * @author 张晓达
 */
public record BinaryExpression(
        Expression left,
        BinaryOp operator,
        Expression right,
        SourceLocation location
) implements Expression {
}
