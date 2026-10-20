/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;

/**
 * {@code switch (x) { case A -> 1; default -> { yield 2; } }} used as a value. Sections use the same
 * representation as {@link SwitchStatement}; every arm produces its value with a
 * {@link YieldStatement}.
 *
 * @author 张晓达
 */
public record SwitchExpression(
        Expression selector,
        List<SwitchSection> sections,
        SourceLocation location
) implements Expression {
}
