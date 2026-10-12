/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

/**
 * @author 张晓达
 */
public record LiteralExpression(
        Kind kind,
        Object value,
        SourceLocation location
) implements Expression {

    public enum Kind {
        INT,
        LONG,
        FLOAT,
        DOUBLE,
        BOOLEAN,
        STRING
    }

    public static LiteralExpression ofInt(int value, SourceLocation loc) {
        return new LiteralExpression(Kind.INT, value, loc);
    }

    public static LiteralExpression ofLong(long value, SourceLocation loc) {
        return new LiteralExpression(Kind.LONG, value, loc);
    }

    public static LiteralExpression ofFloat(float value, SourceLocation loc) {
        return new LiteralExpression(Kind.FLOAT, value, loc);
    }

    public static LiteralExpression ofDouble(double value, SourceLocation loc) {
        return new LiteralExpression(Kind.DOUBLE, value, loc);
    }

    public static LiteralExpression ofBoolean(boolean value, SourceLocation loc) {
        return new LiteralExpression(Kind.BOOLEAN, value, loc);
    }

    public static LiteralExpression ofString(String value, SourceLocation loc) {
        return new LiteralExpression(Kind.STRING, value, loc);
    }
}
