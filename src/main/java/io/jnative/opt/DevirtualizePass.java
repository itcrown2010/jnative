package io.jnative.opt;

import io.jnative.ir.IrBasicBlock;
import io.jnative.ir.IrFunction;
import io.jnative.ir.IrInstruction;
import io.jnative.ir.IrValue;
import io.jnative.vtable.VTable;

import java.util.ArrayList;
import java.util.List;

/** Devirtualize monomorphic virtual calls. */
public final class DevirtualizePass implements OptimizationPass {
    @Override
    public String name() {
        return "devirtualize";
    }

    @Override
    public void run(OptimizationContext ctx) {
        for (IrFunction function : ctx.module().functions()) {
            devirtualize(function, ctx);
        }
    }

    private void devirtualize(IrFunction function, OptimizationContext ctx) {
        for (IrBasicBlock block : function.blocks()) {
            for (int i = 0; i < block.instructions().size(); i++) {
                IrInstruction inst = block.instructions().get(i);
                if (!(inst instanceof IrInstruction.VirtualCall vc)) {
                    continue;
                }
                String target = resolveTarget(ctx, function, vc);
                if (target == null) {
                    continue;
                }
                IrInstruction.Call call = new IrInstruction.Call(
                        vc.dest(), target, vc.returnType(), buildArgs(vc));
                block.instructions().set(i, call);
                ctx.report().increment("devirtualize");
            }
        }
    }

    private String resolveTarget(OptimizationContext ctx, IrFunction function, IrInstruction.VirtualCall vc) {
        String methodName = methodNameFromDebug(vc.debugName());
        if (vc.receiver() instanceof IrValue.Temp t) {
            String exact = ctx.exactClass(function, t);
            if (exact == null) {
                exact = IrUtil.traceToNewObjectClass(function, t);
            }
            if (exact != null) {
                VTable vt = ctx.module().vtables().get(exact);
                if (vt != null && vc.slot() < vt.size()) {
                    return vt.entries().get(vc.slot()).mangledImplementation();
                }
            }
        }
        return null;
    }

    private static String methodNameFromDebug(String debugName) {
        if (debugName == null || !debugName.contains(".")) {
            return null;
        }
        return debugName.substring(debugName.indexOf('.') + 1);
    }

    private List<IrValue> buildArgs(IrInstruction.VirtualCall vc) {
        if (!vc.args().isEmpty()) {
            return vc.args();
        }
        return List.of(vc.receiver());
    }
}
