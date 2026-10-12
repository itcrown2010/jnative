/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.semantic;

import io.jnative.diagnostics.SourceLocation;

/**
 * Shared identity for class and interface reference types (0.14).
 * @author 张晓达
 */
public sealed interface ReferenceTypeSymbol permits ClassSymbol, InterfaceSymbol {
    String name();

    SourceLocation location();

    /** ClassType or InterfaceType. */
    JType asJType();

    boolean isInterface();
}
