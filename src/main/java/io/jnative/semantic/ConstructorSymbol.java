/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.semantic;

import io.jnative.diagnostics.SourceLocation;
import io.jnative.mangle.NameMangler;

import java.util.List;

/**
 * Constructor symbol. Native ABI: void _JN_Class_init[_sig](ptr this, ...)
 * @author 张晓达
 */
public final class ConstructorSymbol {
    private final ClassSymbol owner;
    private final List<ParameterSymbol> parameters;
    private final String mangledName;
    private final SourceLocation location;
    private final boolean isImplicit;
    private ConstructorSymbol delegatesTo;
    private boolean delegatesToSuper;
    private boolean varargs;

    public boolean isVarargs() {
        return varargs;
    }

    public void setVarargs(boolean varargs) {
        this.varargs = varargs;
    }

    public ConstructorSymbol(
            ClassSymbol owner,
            List<ParameterSymbol> parameters,
            boolean isImplicit,
            SourceLocation location
    ) {
        this.owner = owner;
        this.parameters = List.copyOf(parameters);
        this.isImplicit = isImplicit;
        this.location = location;
        this.mangledName = NameMangler.mangleConstructor(
                owner.name(),
                parameters.stream().map(ParameterSymbol::type).toList());
    }

    public ClassSymbol owner() {
        return owner;
    }

    public List<ParameterSymbol> parameters() {
        return parameters;
    }

    public List<JType> parameterTypes() {
        return parameters.stream().map(ParameterSymbol::type).toList();
    }

    public String mangledName() {
        return mangledName;
    }

    public SourceLocation location() {
        return location;
    }

    public boolean isImplicit() {
        return isImplicit;
    }

    public ConstructorSymbol delegatesTo() {
        return delegatesTo;
    }

    public void setDelegatesTo(ConstructorSymbol delegatesTo) {
        this.delegatesTo = delegatesTo;
    }

    public boolean delegatesToSuper() {
        return delegatesToSuper;
    }

    public void setDelegatesToSuper(boolean delegatesToSuper) {
        this.delegatesToSuper = delegatesToSuper;
    }
}
