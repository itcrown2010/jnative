package io.jnative.vtable;

import io.jnative.semantic.MethodSymbol;

/**
 * One slot in a class vtable.
 * @author 张晓达
 */
public record VTableEntry(
        int slot,
        MethodSymbol slotOwner,
        MethodSymbol implementation
) {
    public String mangledImplementation() {
        return implementation.mangledName();
    }
}
