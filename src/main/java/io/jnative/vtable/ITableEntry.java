package io.jnative.vtable;

import io.jnative.semantic.MethodSymbol;

/**
 * @author 张晓达
 */
public record ITableEntry(int slot, MethodSymbol implementation) {
    public String mangledImplementation() {
        return implementation.mangledName();
    }
}
