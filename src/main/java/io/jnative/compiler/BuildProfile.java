/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.compiler;

/**
 * Native build profiles for Runtime Stability 0.8.
 * Sanitizer modes are mutually exclusive with each other where required
 * (ASan/UBSan may combine; TSan must run alone).
 * @author 张晓达
 */
public enum BuildProfile {
    DEBUG,
    RELEASE
}