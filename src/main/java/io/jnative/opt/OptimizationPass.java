package io.jnative.opt;

/** One IR optimization pass.
 * @author 张晓达
 */
public interface OptimizationPass {
    String name();

    void run(OptimizationContext ctx);
}
