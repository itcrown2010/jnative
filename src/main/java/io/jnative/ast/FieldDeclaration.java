/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;
import io.jnative.semantic.Visibility;

/**
 * @author 张晓达
 */
public record FieldDeclaration(
        TypeName type,
        boolean isArray,
        String name,
        Visibility visibility,
        boolean isStatic,
        boolean isVolatile,
        Expression initializer,
        SourceLocation location
) implements AstNode {
    public FieldDeclaration(
            TypeName type,
            boolean isArray,
            String name,
            boolean isStatic,
            boolean isVolatile,
            Expression initializer,
            SourceLocation location
    ) {
        this(type, isArray, name, Visibility.PACKAGE, isStatic, isVolatile, initializer, location);
    }
}
