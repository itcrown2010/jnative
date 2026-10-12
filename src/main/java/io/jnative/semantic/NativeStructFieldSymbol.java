/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.semantic;

import io.jnative.diagnostics.SourceLocation;
import io.jnative.ffi.AbiType;

/**
 * Field of a {@link NativeStructSymbol}. Not a managed {@link FieldSymbol}.
 * @author 张晓达
 */
public final class NativeStructFieldSymbol {
    private final NativeStructSymbol owner;
    private final String name;
    private final JType type;
    private final int declaredIndex;
    private final SourceLocation location;
    private long offset = -1;
    private AbiType abiType;

    public NativeStructFieldSymbol(
            NativeStructSymbol owner,
            String name,
            JType type,
            int declaredIndex,
            SourceLocation location
    ) {
        this.owner = owner;
        this.name = name;
        this.type = type;
        this.declaredIndex = declaredIndex;
        this.location = location;
    }

    public NativeStructSymbol owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public JType type() {
        return type;
    }

    public int index() {
        return declaredIndex;
    }

    public SourceLocation location() {
        return location;
    }

    public long offset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public AbiType abiType() {
        return abiType;
    }

    public void setAbiType(AbiType abiType) {
        this.abiType = abiType;
    }
}
