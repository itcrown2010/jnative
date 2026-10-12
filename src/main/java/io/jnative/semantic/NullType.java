/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.semantic;

/**
 * @author 张晓达
 */
public enum NullType implements JType {
    INSTANCE;

    @Override
    public String displayName() {
        return "null";
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isReference() {
        return true;
    }

    @Override
    public boolean isNumeric() {
        return false;
    }

    @Override
    public boolean isInteger() {
        return false;
    }

    @Override
    public boolean isAssignableFrom(JType other) {
        return other instanceof NullType || other instanceof ErrorType;
    }

    @Override
    public String toString() {
        return displayName();
    }
}
