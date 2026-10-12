/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;

/**
 * Method invocation. {@code receiver == null} means static / unqualified call.
 * {@code typeArguments} supports compiler-recognized forms like {@code Native.allocate<Point>()}.
 * @author 张晓达
 */
public record MethodCallExpression(
        Expression receiver,
        String name,
        List<Expression> arguments,
        List<TypeName> typeArguments,
        SourceLocation location
) implements Expression {
    public MethodCallExpression(
            Expression receiver,
            String name,
            List<Expression> arguments,
            SourceLocation location
    ) {
        this(receiver, name, arguments, List.of(), location);
    }
}
