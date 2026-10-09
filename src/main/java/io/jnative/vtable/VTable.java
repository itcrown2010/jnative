package io.jnative.vtable;

import io.jnative.semantic.ClassSymbol;

import java.util.List;
import java.util.Optional;

/**
 * Virtual method table for a class. Slot indices are stable across subclasses
 * for inherited/overridden methods; new virtual methods append slots.
 * @author 张晓达
 */
public final class VTable {
    private final ClassSymbol owner;
    private final List<VTableEntry> entries;
    private final String globalName;

    public VTable(ClassSymbol owner, List<VTableEntry> entries) {
        this.owner = owner;
        this.entries = List.copyOf(entries);
        this.globalName = "vtable." + owner.name();
    }

    public ClassSymbol owner() {
        return owner;
    }

    public List<VTableEntry> entries() {
        return entries;
    }

    public String globalName() {
        return globalName;
    }

    public int size() {
        return entries.size();
    }

    public Optional<VTableEntry> entry(int slot) {
        if (slot < 0 || slot >= entries.size()) {
            return Optional.empty();
        }
        return Optional.of(entries.get(slot));
    }
}
