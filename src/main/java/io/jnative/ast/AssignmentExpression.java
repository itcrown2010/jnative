/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * Assignment to a variable or field (lvalue is an expression).
 * {@code postfix} marks the desugared {@code x++} / {@code x--} whose value is the OLD value of the target.
 * @author 张晓达
 */
public record AssignmentExpression(
        Expression target,
        Expression value,
        boolean postfix,
        SourceLocation location
) implements Expression {
    public AssignmentExpression(Expression target, Expression value, SourceLocation location) {
        this(target, value, false, location);
    }

    /** Same assignment without the old-value result (statement position). */
    public AssignmentExpression asStatement() {
        return postfix ? new AssignmentExpression(target, value, false, location) : this;
    }
}
