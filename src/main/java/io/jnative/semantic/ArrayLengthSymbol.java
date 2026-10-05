package io.jnative.semantic;

/**
 * Marker resolved for {@code array.length} — not a normal instance field.
 */
public final class ArrayLengthSymbol {
    public static final ArrayLengthSymbol INSTANCE = new ArrayLengthSymbol();

    private ArrayLengthSymbol() {
    }
}
