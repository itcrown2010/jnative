/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * @author 张晓达
 */
public record Parameter(
        TypeName type,
        String name,
        boolean isArray,
        boolean isFinal,
        SourceLocation location
) implements AstNode {
    public Parameter(TypeName type, String name, boolean isArray, SourceLocation location) {
        this(type, name, isArray, false, location);
    }
}
