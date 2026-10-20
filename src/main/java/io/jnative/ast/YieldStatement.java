/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * {@code yield value;} inside a switch expression (also the desugared form of an arrow arm
 * {@code case X -> value;}).
 *
 * @author 张晓达
 */
public record YieldStatement(Expression value, SourceLocation location) implements Statement {
}
