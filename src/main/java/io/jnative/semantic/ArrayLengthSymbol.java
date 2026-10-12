/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.semantic;

/**
 * Marker resolved for {@code array.length} — not a normal instance field.
 * @author 张晓达
 */
public final class ArrayLengthSymbol {
    public static final ArrayLengthSymbol INSTANCE = new ArrayLengthSymbol();

    private ArrayLengthSymbol() {
    }
}
