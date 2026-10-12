/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.meta;

/**
 * Annotation retention. Default for framework annotations is {@link #COMPILE_TIME}.
 * {@link #RUNTIME_METADATA} is lightweight selected schema only — not JVM reflection.
 * @author 张晓达
 */
public enum RetentionPolicy {
    SOURCE,
    COMPILE_TIME,
    RUNTIME_METADATA
}
