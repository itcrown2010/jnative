/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

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
