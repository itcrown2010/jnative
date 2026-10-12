/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.semantic;

import java.util.List;

/**
 * Resolved target of an interface method call (IR lowers to {@code InterfaceCall}).
 * @author 张晓达
 */
public record InterfaceCallTarget(
        InterfaceSymbol interfaceSymbol,
        int slot,
        MethodSymbol interfaceMethod,
        JType returnType,
        List<JType> parameterTypes
) {
}
