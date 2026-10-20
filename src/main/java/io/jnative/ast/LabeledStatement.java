/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * {@code label: statement} — the target of {@code break label;} / {@code continue label;}.
 *
 * @author 张晓达
 */
public record LabeledStatement(String label, Statement body, SourceLocation location) implements Statement {
}
