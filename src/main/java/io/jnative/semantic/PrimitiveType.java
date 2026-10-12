/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.semantic;

/**
 * @author 张晓达
 */
public enum PrimitiveType implements JType {
    BYTE("byte"),
    SHORT("short"),
    INT("int"),
    LONG("long"),
    FLOAT("float"),
    DOUBLE("double"),
    BOOLEAN("boolean"),
    CHAR("char");

    private final String display;

    PrimitiveType(String display) {
        this.display = display;
    }

    @Override
    public String displayName() {
        return display;
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

    @Override
    public boolean isReference() {
        return false;
    }

    @Override
    public boolean isNumeric() {
        return this != BOOLEAN;
    }

    @Override
    public boolean isInteger() {
        return this == BYTE || this == SHORT || this == INT || this == LONG || this == CHAR;
    }

    @Override
    public boolean isAssignableFrom(JType other) {
        if (other instanceof ErrorType) {
            return true;
        }
        if (other == this) {
            return true;
        }
        if (!(other instanceof PrimitiveType op)) {
            return false;
        }
        // char <-> int widening loosely
        if (this == INT && (op == CHAR || op == BYTE || op == SHORT)) {
            return true;
        }
        // Allow int literals / values into byte/short/char (narrowing, Java subset).
        if ((this == BYTE || this == SHORT || this == CHAR) && op == INT) {
            return true;
        }
        if (this == SHORT && op == BYTE) {
            return true;
        }
        if (this == LONG && (op == INT || op == CHAR || op == BYTE || op == SHORT)) {
            return true;
        }
        if (this == FLOAT && (op == INT || op == LONG || op == CHAR || op == BYTE || op == SHORT)) {
            return true;
        }
        if (this == DOUBLE && (op == INT || op == LONG || op == FLOAT || op == CHAR || op == BYTE || op == SHORT)) {
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return display;
    }
}
