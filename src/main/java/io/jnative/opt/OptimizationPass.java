/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.opt;

/** One IR optimization pass.
 * @author 张晓达
 */
public interface OptimizationPass {
    String name();

    void run(OptimizationContext ctx);
}
