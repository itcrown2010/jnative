/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.iface;

import io.jnative.semantic.InterfaceMethodSlot;
import io.jnative.semantic.InterfaceSymbol;
import io.jnative.semantic.MethodSymbol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assigns stable interface method slots, merging inherited signatures (diamond-safe).
 * @author 张晓达
 */
public final class InterfaceSlotBuilder {

    private InterfaceSlotBuilder() {
    }

    public static void buildSlots(InterfaceSymbol iface) {
        Map<String, MethodSymbol> bySig = new LinkedHashMap<>();
        for (InterfaceSymbol parent : iface.superInterfaces()) {
            for (InterfaceMethodSlot slot : parent.slots()) {
                bySig.putIfAbsent(sigKey(slot.method()), slot.method());
            }
        }
        for (MethodSymbol m : iface.declaredMethods()) {
            bySig.put(sigKey(m), m);
        }
        List<InterfaceMethodSlot> slots = new ArrayList<>();
        int i = 0;
        for (MethodSymbol m : bySig.values()) {
            slots.add(new InterfaceMethodSlot(i++, iface, m));
        }
        iface.setSlots(slots);
    }

    private static String sigKey(MethodSymbol m) {
        StringBuilder sb = new StringBuilder(m.name());
        sb.append('(');
        for (var t : m.parameterTypes()) {
            sb.append(t.displayName()).append(';');
        }
        sb.append(')');
        return sb.toString();
    }
}
