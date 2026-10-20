/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.semantic;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author 张晓达
 */
public final class Scope {
    private final Scope parent;
    private final Map<String, Symbol> symbols = new HashMap<>();

    public Scope(Scope parent) {
        this.parent = parent;
    }

    public boolean declare(Symbol symbol) {
        if (symbols.containsKey(symbol.name())) {
            return false;
        }
        symbols.put(symbol.name(), symbol);
        return true;
    }

    /** Replaces any existing symbol of the same name in this scope (pattern-binding reuse). */
    public void redeclare(Symbol symbol) {
        symbols.put(symbol.name(), symbol);
    }

    public Optional<Symbol> resolve(String name) {
        Symbol local = symbols.get(name);
        if (local != null) {
            return Optional.of(local);
        }
        if (parent != null) {
            return parent.resolve(name);
        }
        return Optional.empty();
    }

    public Optional<Symbol> resolveLocal(String name) {
        return Optional.ofNullable(symbols.get(name));
    }

    public Scope parent() {
        return parent;
    }
}
