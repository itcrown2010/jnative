/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * @author 张晓达
 */
public record VariableDeclaration(
        TypeName type,
        boolean isArray,
        String name,
        Expression initializer,
        boolean stackAllocated,
        boolean isFinal,
        SourceLocation location
) implements Statement {
    public VariableDeclaration(
            TypeName type,
            boolean isArray,
            String name,
            Expression initializer,
            SourceLocation location
    ) {
        this(type, isArray, name, initializer, false, false, location);
    }

    public VariableDeclaration(
            TypeName type,
            boolean isArray,
            String name,
            Expression initializer,
            boolean stackAllocated,
            SourceLocation location
    ) {
        this(type, isArray, name, initializer, stackAllocated, false, location);
    }
}
