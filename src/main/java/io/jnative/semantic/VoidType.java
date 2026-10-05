package io.jnative.semantic;

public enum VoidType implements JType {
    INSTANCE;

    @Override
    public String displayName() {
        return "void";
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isReference() {
        return false;
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
        return other instanceof VoidType || other instanceof ErrorType;
    }

    @Override
    public String toString() {
        return displayName();
    }
}
