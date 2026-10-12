/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.vtable;

import io.jnative.semantic.ClassSymbol;
import io.jnative.semantic.MethodSymbol;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds vtables with stable slots: overrides replace implementation, preserve slot;
 * new virtual methods append.
 * @author 张晓达
 */
public final class VTableBuilder {

    private VTableBuilder() {
    }

    public static VTable build(ClassSymbol classSymbol) {
        List<VTableEntry> entries = new ArrayList<>();

        classSymbol.superClass().ifPresent(superClass -> {
            if (superClass.vtable() != null) {
                for (VTableEntry parent : superClass.vtable().entries()) {
                    entries.add(new VTableEntry(
                            parent.slot(),
                            parent.slotOwner(),
                            parent.implementation()));
                }
            }
        });

        for (MethodSymbol method : classSymbol.declaredMethods()) {
            if (method.isStatic() || method.isMain() || !method.isVirtual() || method.isFfiIntrinsic()) {
                continue;
            }
            MethodSymbol overridden = method.overrides();
            if (overridden != null && overridden.vtableSlot() != null) {
                int slot = overridden.vtableSlot();
                method.setVtableSlot(slot);
                entries.set(slot, new VTableEntry(slot, overridden, method));
            } else {
                int slot = entries.size();
                method.setVtableSlot(slot);
                entries.add(new VTableEntry(slot, method, method));
            }
        }

        return new VTable(classSymbol, entries);
    }
}
