package io.jnative.opt;

import io.jnative.ir.IrBasicBlock;
import io.jnative.ir.IrFunction;
import io.jnative.ir.IrInstruction;
import io.jnative.ir.IrValue;

/** Mark array loads unchecked in simple counted loops.
 * @author 张晓达
 */
public final class BoundsCheckEliminationPass implements OptimizationPass {
    @Override
    public String name() {
        return "bounds-check-elim";
    }

    @Override
    public void run(OptimizationContext ctx) {
        for (IrFunction function : ctx.module().functions()) {
            eliminate(function, ctx);
        }
    }

    private void eliminate(IrFunction function, OptimizationContext ctx) {
        var loopInfo = ctx.getLoopInfo(function);
        for (var backedge : loopInfo.backedges()) {
            markLoop(function, backedge.header(), ctx);
        }
    }

    private void markLoop(IrFunction function, String header, OptimizationContext ctx) {
        IrBasicBlock headerBlock = function.blocks().stream()
                .filter(b -> b.name().equals(header))
                .findFirst().orElse(null);
        if (headerBlock == null) {
            return;
        }
        IrValue loopIndex = findLoopIndex(headerBlock);
        IrValue loopBound = findLoopBound(headerBlock, loopIndex);
        if (loopIndex == null || loopBound == null) {
            return;
        }
        for (IrBasicBlock block : function.blocks()) {
            for (int i = 0; i < block.instructions().size(); i++) {
                IrInstruction inst = block.instructions().get(i);
                if (inst instanceof IrInstruction.LoadElement le
                        && !le.unchecked()
                        && indexMatches(le.index(), loopIndex)) {
                    IrValue bound = findArrayLengthSource(function, le.array());
                    if (bound != null && valuesRelated(bound, loopBound)) {
                        block.instructions().set(i, new IrInstruction.LoadElement(
                                le.dest(), le.array(), le.index(), le.elementType(), true));
                        ctx.report().increment("bounds-check-elim");
                    }
                }
            }
        }
    }

    private IrValue findLoopIndex(IrBasicBlock header) {
        for (IrInstruction inst : header.instructions()) {
            if (inst instanceof IrInstruction.CondBranch cb
                    && cb.condition() instanceof IrValue.Temp t) {
                return t;
            }
        }
        return null;
    }

    private IrValue findLoopBound(IrBasicBlock header, IrValue index) {
        for (IrInstruction inst : header.instructions()) {
            if (inst instanceof IrInstruction.Compare c
                    && (c.left().equals(index) || c.right().equals(index))) {
                return c.left().equals(index) ? c.right() : c.left();
            }
        }
        return null;
    }

    private IrValue findArrayLengthSource(IrFunction function, IrValue array) {
        for (IrBasicBlock block : function.blocks()) {
            for (IrInstruction inst : block.instructions()) {
                if (inst instanceof IrInstruction.ArrayLength al && al.array().equals(array)) {
                    return al.dest();
                }
            }
        }
        return null;
    }

    private boolean indexMatches(IrValue index, IrValue loopIndex) {
        return index.equals(loopIndex);
    }

    private boolean valuesRelated(IrValue a, IrValue b) {
        return a.equals(b);
    }
}
