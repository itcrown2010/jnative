/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.opt;

import java.util.ArrayList;
import java.util.List;

/** Runs optimization passes in registration order.
 * @author 张晓达
 */
public final class PassManager {
    private final List<OptimizationPass> passes = new ArrayList<>();

    public PassManager register(OptimizationPass pass) {
        passes.add(pass);
        return this;
    }

    public void run(OptimizationContext ctx) {
        for (OptimizationPass pass : passes) {
            pass.run(ctx);
            ctx.invalidateAnalyses();
        }
    }
}
