package io.jnative.opt;

import io.jnative.ir.IrBasicBlock;
import io.jnative.ir.IrFunction;
import io.jnative.ir.IrInstruction;
import io.jnative.semantic.FunctionEffects;

/** Remove safepoint polls from no-GC / omit-safepoint functions.
 * @author 张晓达
 */
public final class SafepointEliminationPass implements OptimizationPass {
    @Override
    public String name() {
        return "safepoint-elim";
    }

    @Override
    public void run(OptimizationContext ctx) {
        for (IrFunction function : ctx.module().functions()) {
            if (!mayRemoveSafepoints(function, ctx)) {
                continue;
            }
            for (IrBasicBlock block : function.blocks()) {
                block.instructions().removeIf(inst -> {
                    if (inst instanceof IrInstruction.SafepointPoll) {
                        ctx.report().increment("safepoint-elim");
                        return true;
                    }
                    return false;
                });
            }
        }
    }

    private boolean mayRemoveSafepoints(IrFunction function, OptimizationContext ctx) {
        if (function.omitSafepoints() || function.noGcRegion()) {
            return true;
        }
        FunctionEffects fx = ctx.effects(function.name());
        return fx.isDeterministic() && !fx.maySafepoint() && !fx.mayAllocate();
    }
}
