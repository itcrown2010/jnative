package io.jnative.semantic;

import java.util.List;

/**
 * Resolved target of an interface method call (IR lowers to {@code InterfaceCall}).
 */
public record InterfaceCallTarget(
        InterfaceSymbol interfaceSymbol,
        int slot,
        MethodSymbol interfaceMethod,
        JType returnType,
        List<JType> parameterTypes
) {
}
