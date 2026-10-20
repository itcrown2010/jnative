/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/** {@code break;} (innermost loop/switch) or {@code break label;}.
 * @author 张晓达
 */
public record BreakStatement(String label, SourceLocation location) implements Statement {
    public BreakStatement(SourceLocation location) {
        this(null, location);
    }
}
