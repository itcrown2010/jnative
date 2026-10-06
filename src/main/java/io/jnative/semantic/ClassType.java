package io.jnative.semantic;

/**
 * User-defined or builtin class type (e.g. Person, String).
 */
public record ClassType(ClassSymbol classSymbol) implements JType {

    public String name() {
        return classSymbol.name();
    }

    @Override
    public String displayName() {
        return classSymbol.name();
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
        if (other instanceof ErrorType) {
            return true;
        }
        if (other instanceof NullType) {
            return true;
        }
        if (other instanceof ClassType ct) {
            if (classSymbol.functionType() != null && ct.classSymbol().functionType() != null) {
                return classSymbol.functionType().isAssignableFrom(ct.classSymbol().functionType())
                        || ct.classSymbol().isSubtypeOf(classSymbol);
            }
            return ct.classSymbol().isSubtypeOf(this.classSymbol);
        }
        return false;
    }

    @Override
    public String toString() {
        return displayName();
    }
}
