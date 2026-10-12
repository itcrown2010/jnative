/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ast;

/**
 * @author 张晓达
 */
public enum BinaryOp {
    ADD,
    SUB,
    MUL,
    DIV,
    MOD,
    LT,
    LE,
    GT,
    GE,
    EQ,
    NE,
    /** Logical short-circuit {@code &&}. */
    AND,
    /** Logical short-circuit {@code ||}. */
    OR,
    /** Bitwise / boolean non-short-circuit {@code &}. */
    BIT_AND,
    /** Bitwise / boolean non-short-circuit {@code |}. */
    BIT_OR,
    /** Bitwise / boolean {@code ^}. */
    BIT_XOR,
    SHL,
    /** Arithmetic right shift {@code >>}. */
    SHR,
    /** Logical right shift {@code >>>}. */
    USHR
}
