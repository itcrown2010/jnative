/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * @author 张晓达
 */
public record UnaryExpression(
        UnaryOp operator,
        Expression operand,
        SourceLocation location
) implements Expression {
}
