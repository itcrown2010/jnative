package io.jnative.semantic;

/** Type variable {@code T} from a type parameter declaration. */
public record TypeVariable(TypeParameterSymbol symbol) implements JType {

    public String name() {
        return symbol.name();
    }

    @Override
    public String displayName() {
        return symbol.name();
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isReference() {
        return true; // until specialized; semantic treats as ref until lowering
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
        if (other instanceof ErrorType || other instanceof NullType) {
            return true;
        }
        if (other instanceof TypeVariable tv) {
            return symbol == tv.symbol()
                    || (symbol.name().equals(tv.symbol().name())
                    && sameOwner(symbol, tv.symbol()));
        }
        // Inside a generic body, only values of type T (or null) may be stored in T.
        // Concrete types require substitution at the call site (e.g. Box<String>.set).
        return false;
    }

    private static boolean sameOwner(TypeParameterSymbol a, TypeParameterSymbol b) {
        if (a.ownerType() != null && b.ownerType() != null) {
            return a.ownerType().name().equals(b.ownerType().name());
        }
        if (a.ownerMethod() != null && b.ownerMethod() != null) {
            return a.ownerMethod().name().equals(b.ownerMethod().name())
                    && a.ownerMethod().owner().name().equals(b.ownerMethod().owner().name());
        }
        return a.ownerType() == null && b.ownerType() == null
                && a.ownerMethod() == null && b.ownerMethod() == null;
    }

    @Override
    public String toString() {
        return displayName();
    }
}
