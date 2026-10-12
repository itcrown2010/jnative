/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

import io.jnative.diagnostics.SourceLocation;

import java.util.Optional;

/**
 * Marker / value annotation retained for RPC and related metadata (0.28).
 * Distinct from {@link NativeAnnotation} / {@link EffectContract}.
 * @author 张晓达
 */
public record MarkerAnnotation(
        String name,
        Optional<String> stringValue,
        Optional<Integer> intValue,
        SourceLocation location
) {
    public MarkerAnnotation(String name, SourceLocation location) {
        this(name, Optional.empty(), Optional.empty(), location);
    }

    public MarkerAnnotation(String name, String value, SourceLocation location) {
        this(name, Optional.ofNullable(value), Optional.empty(), location);
    }

    public MarkerAnnotation(String name, int value, SourceLocation location) {
        this(name, Optional.empty(), Optional.of(value), location);
    }
}
