package io.jnative.semantic;

/**
 * One captured variable in a lambda closure environment object.
 * @author 张晓达
 */
public record ClosureCapture(
        VariableSymbol variable,
        String fieldName,
        JType type,
        boolean isReference
) {
}
