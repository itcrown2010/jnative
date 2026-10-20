/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * The {@code super} receiver of {@code super.method(...)}: {@code this} viewed as the superclass,
 * with the call bound statically (no virtual dispatch). Only legal as a method-call receiver.
 *
 * @author 张晓达
 */
public record SuperExpression(SourceLocation location) implements Expression {
}
