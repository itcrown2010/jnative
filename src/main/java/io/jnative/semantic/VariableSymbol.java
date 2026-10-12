/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.semantic;

import io.jnative.diagnostics.SourceLocation;
import io.jnative.ffi.PointerProvenance;

/**
 * @author 张晓达
 */
public final class VariableSymbol {
    public enum Kind {
        LOCAL,
        PARAMETER,
        FIELD_ALIAS // unused; fields use FieldSymbol
    }

    private final String name;
    private final JType type;
    private final Kind kind;
    private final SourceLocation location;
    private final boolean stackAllocated;
    private final boolean isFinal;
    private PointerProvenance provenance = PointerProvenance.UNKNOWN;
    private boolean assignedAfterInit;
    private boolean capturedByLambda;

    public VariableSymbol(String name, JType type, Kind kind, SourceLocation location) {
        this(name, type, kind, location, false, false);
    }

    public VariableSymbol(
            String name,
            JType type,
            Kind kind,
            SourceLocation location,
            boolean stackAllocated
    ) {
        this(name, type, kind, location, stackAllocated, false);
    }

    public VariableSymbol(
            String name,
            JType type,
            Kind kind,
            SourceLocation location,
            boolean stackAllocated,
            boolean isFinal
    ) {
        this.name = name;
        this.type = type;
        this.kind = kind;
        this.location = location;
        this.stackAllocated = stackAllocated;
        this.isFinal = isFinal;
        if (stackAllocated) {
            this.provenance = PointerProvenance.STACK;
        } else if (type instanceof NativePtrType) {
            this.provenance = PointerProvenance.UNKNOWN;
        }
    }

    public boolean isFinal() {
        return isFinal;
    }

    public String name() {
        return name;
    }

    public JType type() {
        return type;
    }

    public Kind kind() {
        return kind;
    }

    public SourceLocation location() {
        return location;
    }

    public boolean isStackAllocated() {
        return stackAllocated;
    }

    public PointerProvenance provenance() {
        return provenance;
    }

    public void setProvenance(PointerProvenance provenance) {
        this.provenance = provenance;
    }

    public boolean isAssignedAfterInit() {
        return assignedAfterInit;
    }

    public void setAssignedAfterInit(boolean assignedAfterInit) {
        this.assignedAfterInit = assignedAfterInit;
    }

    public boolean isCapturedByLambda() {
        return capturedByLambda;
    }

    public void setCapturedByLambda(boolean capturedByLambda) {
        this.capturedByLambda = capturedByLambda;
    }
}
