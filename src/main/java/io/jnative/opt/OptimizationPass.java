package io.jnative.opt;

/** One IR optimization pass. */
public interface OptimizationPass {
    String name();

    void run(OptimizationContext ctx);
}
