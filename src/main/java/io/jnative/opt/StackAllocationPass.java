package io.jnative.opt;

import io.jnative.ir.IrBasicBlock;
import io.jnative.ir.IrFunction;
import io.jnative.ir.IrInstruction;
import io.jnative.layout.ClassLayout;
import io.jnative.layout.FieldLayout;

/**
 * Lowers remaining {@link EscapeState.EscapeKind#NO_ESCAPE} managed allocations to
 * stack storage while retaining ClassMetadata / field semantics.
 * Primitive-only objects are preferably handled by {@link ScalarReplacementPass} first.
 * @author 张晓达
 */
public final class StackAllocationPass implements OptimizationPass {
    @Override
    public String name() {
        return "stack-allocation";
    }

    @Override
    public void run(OptimizationContext ctx) {
        for (IrFunction function : ctx.module().functions()) {
            allocateInFunction(ctx, function);
            ctx.invalidateFunction(function);
        }
    }

    private void allocateInFunction(OptimizationContext ctx, IrFunction function) {
        var escape = ctx.escapeStates(function);
        for (IrBasicBlock block : function.blocks()) {
            var insts = block.instructions();
            for (int i = 0; i < insts.size(); i++) {
                if (!(insts.get(i) instanceof IrInstruction.NewObject n) || n.stackAllocated()) {
                    continue;
                }
                EscapeState st = escape.get(n.dest());
                if (st == null || !st.isNoEscape()) {
                    if (ctx.emitEscapeAnalysis() && st != null) {
                        ctx.report().note("Why wasn't " + n.className() + " stack allocated? escape="
                                + st.kind() + " in " + function.name());
                    }
                    continue;
                }
                ClassLayout layout = ctx.module().classLayouts().get(n.className());
                if (layout == null) {
                    continue;
                }
                // Prefer scalar replacement for primitive-only; still allow stack if it remains.
                insts.set(i, new IrInstruction.NewObject(
                        n.dest(),
                        n.className(),
                        n.llvmTypeName(),
                        n.sizeBytes(),
                        n.metadataGlobal(),
                        true
                ));
                int refFields = 0;
                for (FieldLayout f : layout.fields()) {
                    if (f.isReference()) {
                        refFields++;
                    }
                }
                ctx.report().increment("stack-allocation");
                if (refFields > 0) {
                    ctx.report().increment("stack-allocation-with-refs");
                }
                ctx.report().note("stack-alloc " + n.className()
                        + " refs=" + refFields + " in " + function.name());
            }
        }
    }
}
