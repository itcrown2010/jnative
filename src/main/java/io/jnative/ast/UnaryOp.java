/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

/**
 * @author 张晓达
 */
public enum UnaryOp {
    NOT,
    NEGATE,
    PLUS,
    /** Bitwise complement {@code ~}. */
    BIT_NOT
}
