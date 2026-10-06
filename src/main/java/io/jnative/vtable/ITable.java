package io.jnative.vtable;

import io.jnative.semantic.InterfaceSymbol;

import java.util.List;

/**
 * Per-class interface method table for one implemented interface.
 */
public final class ITable {
    private final InterfaceSymbol interfaceSymbol;
    private final List<ITableEntry> entries;
    private final String globalName;

    public ITable(String ownerClassName, InterfaceSymbol interfaceSymbol, List<ITableEntry> entries) {
        this.interfaceSymbol = interfaceSymbol;
        this.entries = List.copyOf(entries);
        this.globalName = "itable." + ownerClassName + "." + interfaceSymbol.name();
    }

    public InterfaceSymbol interfaceSymbol() {
        return interfaceSymbol;
    }

    public List<ITableEntry> entries() {
        return entries;
    }

    public String globalName() {
        return globalName;
    }

    public int size() {
        return entries.size();
    }
}
