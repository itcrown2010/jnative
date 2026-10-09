package io.jnative.opt;

import io.jnative.ir.IrBasicBlock;
import io.jnative.ir.IrFunction;
import io.jnative.ir.IrInstruction;
import io.jnative.ir.IrValue;

import java.util.HashSet;
import java.util.Set;

/** Remove redundant null checks on freshly allocated or already checked values.
 * @author 张晓达
 */
public final class NullCheckEliminationPass implements OptimizationPass {
    @Override
    public String name() {
        return "null-check-elim";
    }

    @Override
    public void run(OptimizationContext ctx) {
        for (IrFunction function : ctx.module().functions()) {
            eliminate(function, ctx);
        }
    }

    private void eliminate(IrFunction function, OptimizationContext ctx) {
        Set<IrValue.Temp> neverNull = new HashSet<>();
        for (IrBasicBlock block : function.blocks()) {
            for (IrInstruction inst : block.instructions()) {
                if (inst instanceof IrInstruction.NewObject n) {
                    neverNull.addAll(IrUtil.aliasTempsOfNewObject(function, n.dest()));
                } else if (inst instanceof IrInstruction.NewArray n) {
                    neverNull.add(n.dest());
                } else if (inst instanceof IrInstruction.StringLiteral s) {
                    neverNull.add(s.dest());
                }
            }
        }

        for (IrBasicBlock block : function.blocks()) {
            Set<IrValue> checkedInBlock = new HashSet<>();
            for (int i = 0; i < block.instructions().size(); i++) {
                IrInstruction inst = block.instructions().get(i);
                if (!(inst instanceof IrInstruction.NullCheck nc)) {
                    continue;
                }
                IrValue ptr = nc.pointer();
                if (ptr instanceof IrValue.Temp t && neverNull.contains(t)) {
                    block.instructions().remove(i);
                    i--;
                    ctx.report().increment("null-check-elim");
                } else if (checkedInBlock.contains(ptr)) {
                    block.instructions().remove(i);
                    i--;
                    ctx.report().increment("null-check-elim");
                } else {
                    checkedInBlock.add(ptr);
                }
            }
        }
    }
}
