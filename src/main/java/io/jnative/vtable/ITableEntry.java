package io.jnative.vtable;

import io.jnative.semantic.MethodSymbol;

public record ITableEntry(int slot, MethodSymbol implementation) {
    public String mangledImplementation() {
        return implementation.mangledName();
    }
}
