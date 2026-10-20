/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/** {@code continue;} (innermost loop) or {@code continue label;}.
 * @author 张晓达
 */
public record ContinueStatement(String label, SourceLocation location) implements Statement {
    public ContinueStatement(SourceLocation location) {
        this(null, location);
    }
}
