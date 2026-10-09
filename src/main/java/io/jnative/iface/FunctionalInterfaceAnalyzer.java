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
            if (!countsAsAbstractSamCandidate(m)) {
                continue;
            }
            // Equivalent inherited declarations collapse by signature key.
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
        Map<String, MethodSymbol> seen = new LinkedHashMap<>();
        for (InterfaceMethodSlot slot : iface.slots()) {
            MethodSymbol m = slot.method();
            if (!countsAsAbstractSamCandidate(m)) {
                continue;
            }
            seen.putIfAbsent(sigKey(m), m);
        }
        out.addAll(seen.values());
        return out;
    }

    /** Abstract candidates only — Object methods excluded; non-abstract defaults (when present) skipped. */
    private static boolean countsAsAbstractSamCandidate(MethodSymbol m) {
        if (isObjectMethod(m)) {
            return false;
        }
        // Default/interface bodies are not modeled yet; treat non-abstract as non-SAM-candidate.
        return m.isAbstract() || m.interfaceOwner() != null;
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
