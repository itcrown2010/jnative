/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;
import java.util.List;

/**
 * @author 张晓达
 */
public record ForStatement(
        Statement initializer,
        Expression condition,
        Expression update,
        Statement body,
        SourceLocation location,
        List<Expression> moreUpdates
) implements Statement {
    public ForStatement(
            Statement initializer, Expression condition, Expression update, Statement body, SourceLocation location) {
        this(initializer, condition, update, body, location, List.of());
    }

    /** All update expressions in source order ({@code for (...; ...; i++, j--)}). */
    public List<Expression> allUpdates() {
        if (update == null) {
            return List.of();
        }
        java.util.ArrayList<Expression> all = new java.util.ArrayList<>();
        all.add(update);
        all.addAll(moreUpdates);
        return all;
    }
}
