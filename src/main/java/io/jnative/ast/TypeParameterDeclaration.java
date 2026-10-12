/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;

/** Source-level type parameter: {@code T} or {@code T extends Animal}.
 * @author 张晓达
 */
public record TypeParameterDeclaration(
        String name,
        List<TypeName> bounds,
        SourceLocation location
) implements AstNode {
}
