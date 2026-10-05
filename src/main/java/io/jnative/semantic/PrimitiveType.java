package io.jnative.semantic;

public enum PrimitiveType implements JType {
    INT("int"),
    LONG("long"),
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
        return this == INT || this == LONG || this == DOUBLE || this == CHAR;
    }

    @Override
    public boolean isInteger() {
        return this == INT || this == LONG || this == CHAR;
    }

    @Override
    public boolean isAssignableFrom(JType other) {
        if (other instanceof ErrorType) {
            return true;
        }
        if (other == this) {
            return true;
        }
        // char <-> int widening loosely for 0.4
        if (this == INT && other == CHAR) {
            return true;
        }
        if (this == CHAR && other == INT) {
            return true; // allow int literal to char in simple cases
        }
        if (this == LONG && (other == INT || other == CHAR)) {
            return true;
        }
        if (this == DOUBLE && (other == INT || other == LONG || other == CHAR)) {
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return display;
    }
}
