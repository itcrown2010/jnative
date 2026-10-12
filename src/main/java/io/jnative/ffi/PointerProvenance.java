/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ffi;

/**
 * Provenance of a NativePtr value for escape / lifetime analysis.
 * @author 张晓达
 */
public enum PointerProvenance {
    STACK,
    NATIVE_HEAP,
    EXTERNAL,
    NULL,
    UNKNOWN
}
