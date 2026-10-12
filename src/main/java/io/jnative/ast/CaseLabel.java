/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * {@code case <constant>:}. For integer switch, {@code constant} is a compile-time int expression.
 * For enum switch, {@code constant} is typically a simple {@link VariableExpression} naming the constant.
 * @author 张晓达
 */
public record CaseLabel(Expression constant, SourceLocation location) implements SwitchLabel {
}
