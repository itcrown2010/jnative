package io.jnative.iface;

import io.jnative.semantic.FunctionType;
import io.jnative.semantic.InterfaceMethodSlot;
import io.jnative.semantic.InterfaceSymbol;
import io.jnative.semantic.MethodSymbol;
import io.jnative.semantic.VoidType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SAM detection for interfaces (considers inheritance; ignores Object-like noise).
 */
public final class FunctionalInterfaceAnalyzer {

    private FunctionalInterfaceAnalyzer() {
    }

    public static void analyze(InterfaceSymbol iface) {
        Map<String, MethodSymbol> abstractMethods = new LinkedHashMap<>();
        for (InterfaceMethodSlot slot : iface.slots()) {
            MethodSymbol m = slot.method();
            if (isObjectMethod(m)) {
                continue;
            }
            abstractMethods.put(sigKey(m), m);
        }
        if (abstractMethods.size() == 1) {
            MethodSymbol sam = abstractMethods.values().iterator().next();
            iface.setFunctionType(new FunctionType(
                    sam.parameterTypes(),
                    sam.returnType() instanceof VoidType ? VoidType.INSTANCE : sam.returnType(),
                    FunctionType.Domain.MANAGED));
        } else {
            iface.setFunctionType(null);
        }
    }

    public static List<MethodSymbol> abstractMethods(InterfaceSymbol iface) {
        List<MethodSymbol> out = new ArrayList<>();
        for (InterfaceMethodSlot slot : iface.slots()) {
            if (!isObjectMethod(slot.method())) {
                out.add(slot.method());
            }
        }
        return out;
    }

    private static boolean isObjectMethod(MethodSymbol m) {
        String n = m.name();
        if ("toString".equals(n) && m.parameterTypes().isEmpty()) {
            return true;
        }
        if ("hashCode".equals(n) && m.parameterTypes().isEmpty()) {
            return true;
        }
        if ("equals".equals(n) && m.parameterTypes().size() == 1) {
            return true;
        }
        return false;
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
