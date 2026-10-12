/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * Special-cased System.out.println(...) for JNative 0.1.
 * @author 张晓达
 */
public record PrintlnExpression(Expression argument, SourceLocation location) implements Expression {
}
