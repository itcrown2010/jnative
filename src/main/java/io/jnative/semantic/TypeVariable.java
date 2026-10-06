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
            return symbol == tv.symbol() || symbol.name().equals(tv.symbol().name());
        }
        // Concrete types assigned into T checked via inference/substitution context.
        return true;
    }

    @Override
    public String toString() {
        return displayName();
    }
}
