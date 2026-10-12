/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

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
