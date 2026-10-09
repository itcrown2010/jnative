package io.jnative.opt;

import io.jnative.ir.IrBasicBlock;
import io.jnative.ir.IrFunction;
import io.jnative.ir.IrInstruction;
import io.jnative.ir.IrValue;

import java.util.HashMap;
import java.util.Map;

/** Fold constant binary/compare ops and simplify conditional branches.
 * @author 张晓达
 */
public final class ConstantPropagationPass implements OptimizationPass {
    @Override
    public String name() {
        return "constant-propagation";
    }

    @Override
    public void run(OptimizationContext ctx) {
        for (IrFunction function : ctx.module().functions()) {
            propagate(function, ctx);
        }
    }

    private void propagate(IrFunction function, OptimizationContext ctx) {
        Map<IrValue.Temp, IrValue.Const> known = new HashMap<>();
        for (IrBasicBlock block : function.blocks()) {
            for (IrInstruction inst : block.instructions()) {
                collectKnown(inst, known, ctx);
            }
        }
        if (known.isEmpty()) {
            return;
        }

        for (IrBasicBlock block : function.blocks()) {
            for (int i = 0; i < block.instructions().size(); i++) {
                IrInstruction inst = block.instructions().get(i);
                if (inst instanceof IrInstruction.CondBranch cb) {
                    IrValue cond = resolve(cb.condition(), known);
                    if (cond instanceof IrValue.Const c) {
                        boolean taken = truthy(c);
                        block.instructions().set(i, new IrInstruction.Branch(taken ? cb.trueTarget() : cb.falseTarget()));
                        ctx.report().increment("const-fold-branch");
                    }
                } else {
                    block.instructions().set(i, substituteUses(inst, known));
                }
            }
            block.recalcTerminated();
        }
    }

    private void collectKnown(
            IrInstruction inst,
            Map<IrValue.Temp, IrValue.Const> known,
            OptimizationContext ctx
    ) {
        switch (inst) {
            case IrInstruction.Binary b -> {
                IrValue left = resolve(b.left(), known);
                IrValue right = resolve(b.right(), known);
                if (left instanceof IrValue.Const lc && right instanceof IrValue.Const rc) {
                    IrValue.Const folded = IrUtil.foldBinary(b.op(), lc, rc);
                    if (folded != null) {
                        known.put(b.dest(), folded);
                        ctx.report().increment("const-fold-binary");
                    }
                }
            }
            case IrInstruction.Compare c -> {
                IrValue left = resolve(c.left(), known);
                IrValue right = resolve(c.right(), known);
                if (left instanceof IrValue.Const lc && right instanceof IrValue.Const rc) {
                    IrValue.Const folded = IrUtil.foldCompare(c.op(), lc, rc);
                    if (folded != null) {
                        known.put(c.dest(), folded);
                        ctx.report().increment("const-fold-compare");
                    }
                }
            }
            case IrInstruction.Cast cast -> {
                IrValue src = resolve(cast.source(), known);
                if (src instanceof IrValue.Const c && cast.source().type() == cast.target()) {
                    known.put(cast.dest(), c);
                }
            }
            default -> { }
        }
    }

    private static IrValue resolve(IrValue value, Map<IrValue.Temp, IrValue.Const> known) {
        if (value instanceof IrValue.Temp t) {
            IrValue.Const c = known.get(t);
            if (c != null) {
                return c;
            }
        }
        return value;
    }

    private static IrValue substitute(IrValue value, Map<IrValue.Temp, IrValue.Const> known) {
        if (value instanceof IrValue.Temp t) {
            IrValue.Const c = known.get(t);
            if (c != null) {
                return c;
            }
        }
        return value;
    }

    private static IrInstruction substituteUses(IrInstruction inst, Map<IrValue.Temp, IrValue.Const> known) {
        return IrUtil.mapValues(inst, v -> {
            if (v instanceof IrValue.Temp t) {
                IrValue.Const c = known.get(t);
                if (c != null) {
                    return c;
                }
            }
            return v;
        });
    }

    private static boolean truthy(IrValue.Const c) {
        if (c.type() == io.jnative.ir.IrType.I1) {
            return Boolean.TRUE.equals(c.value());
        }
        return ((Number) c.value()).longValue() != 0;
    }
}
