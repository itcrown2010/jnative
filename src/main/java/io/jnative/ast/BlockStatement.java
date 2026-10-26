/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.List;

/**
 * A braced block. {@code endLocation} is the closing brace (for debug info: the implicit return of a method is
 * attributed to it so a debugger can stop on the final {@code }}); {@link SourceLocation#UNKNOWN} when not known.
 * @author 张晓达
 */
public record BlockStatement(List<Statement> statements, SourceLocation location, SourceLocation endLocation)
        implements Statement {
    public BlockStatement(List<Statement> statements, SourceLocation location) {
        this(statements, location, SourceLocation.UNKNOWN);
    }
}
