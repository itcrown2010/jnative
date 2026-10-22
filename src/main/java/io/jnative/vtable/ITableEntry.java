/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.vtable;

import io.jnative.semantic.MethodSymbol;

/**
 * @author 张晓达
 */
public record ITableEntry(int slot, MethodSymbol implementation, String bridge) {
    public ITableEntry(int slot, MethodSymbol implementation) {
        this(slot, implementation, null);
    }

    /** Symbol stored in the itable: a bridge (boxing adapter for erased generic slots) when present. */
    public String mangledImplementation() {
        return bridge != null ? bridge : implementation.mangledName();
    }
}
