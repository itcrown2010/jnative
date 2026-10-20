/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;

/**
 * {@code new T[len]} or {@code new T[len][len2]...[]}: the result has type {@code elementType[]}.
 * {@code innerLengths} are the sizes of the next (nested) dimensions; {@code elementType} already
 * contains the nested array layers (including trailing unsized ones such as {@code new int[3][]}).
 *
 * @author 张晓达
 */
public record NewArrayExpression(
        TypeName elementType,
        Expression length,
        List<Expression> innerLengths,
        SourceLocation location
) implements Expression {
    public NewArrayExpression(TypeName elementType, Expression length, SourceLocation location) {
        this(elementType, length, List.of(), location);
    }
}
