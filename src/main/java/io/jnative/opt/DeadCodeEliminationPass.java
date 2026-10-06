package io.jnative.opt;

import io.jnative.ir.IrBasicBlock;
import io.jnative.ir.IrFunction;
import io.jnative.ir.IrInstruction;
import io.jnative.ir.IrValue;

import java.util.HashSet;
import java.util.Set;

/** Remove unused pure computations and dead instructions. */
public final class DeadCodeEliminationPass implements OptimizationPass {
    @Override
    public String name() {
        return "dce";
    }

    @Override
    public void run(OptimizationContext ctx) {
        for (IrFunction function : ctx.module().functions()) {
            eliminate(function, ctx);
            ctx.invalidateFunction(function);
        }
    }

    private void eliminate(IrFunction function, OptimizationContext ctx) {
        boolean changed = true;
        while (changed) {
            changed = false;
            Set<IrValue.Temp> used = computeUsed(function);
            for (IrBasicBlock block : function.blocks()) {
                for (int i = 0; i < block.instructions().size(); i++) {
                    IrInstruction inst = block.instructions().get(i);
                    if (isTerminator(inst)) {
                        continue;
                    }
                    if (mustKeep(function, inst, ctx)) {
                        continue;
                    }
                    IrValue.Temp def = IrUtil.definingTemp(inst);
                    if (def != null && !used.contains(def)) {
                        block.instructions().remove(i);
                        i--;
                        changed = true;
                        ctx.report().increment("dce-remove");
                    } else if (def == null && !IrUtil.hasSideEffects(inst, ctx)) {
                        block.instructions().remove(i);
                        i--;
                        changed = true;
                        ctx.report().increment("dce-remove");
                    }
                }
                block.recalcTerminated();
            }
        }
    }

    private static boolean isTerminator(IrInstruction inst) {
        return inst instanceof IrInstruction.Branch
                || inst instanceof IrInstruction.CondBranch
                || inst instanceof IrInstruction.Return;
    }

    private static boolean mustKeep(IrFunction function, IrInstruction inst, OptimizationContext ctx) {
        if (inst instanceof IrInstruction.Alloca
                || inst instanceof IrInstruction.StackAlloc
                || inst instanceof IrInstruction.JumpLabel
                || inst instanceof IrInstruction.Branch
                || inst instanceof IrInstruction.CondBranch
                || inst instanceof IrInstruction.Return
                || inst instanceof IrInstruction.Throw
                || inst instanceof IrInstruction.Println
                || inst instanceof IrInstruction.GcCollect
                || inst instanceof IrInstruction.SafepointPoll
                || inst instanceof IrInstruction.Store
                || inst instanceof IrInstruction.StoreField
                || inst instanceof IrInstruction.StoreElement
                || inst instanceof IrInstruction.StoreStatic
                || inst instanceof IrInstruction.NativeStore
                || inst instanceof IrInstruction.NullCheck
                || inst instanceof IrInstruction.ConstructorCall
                || inst instanceof IrInstruction.NewObject
                || inst instanceof IrInstruction.NewArray
                || inst instanceof IrInstruction.MakeCallable
                || inst instanceof IrInstruction.CreateNativeCallback
                || inst instanceof IrInstruction.ReleaseNativeCallback
                || inst instanceof IrInstruction.ClearException
                || inst instanceof IrInstruction.InvokeCallable
                || inst instanceof IrInstruction.NativeCall) {
            return true;
        }
        if (inst instanceof IrInstruction.Call c) {
            if (c.returnType() == io.jnative.ir.IrType.VOID) {
                return true;
            }
            return !ctx.isPureCall(c.function());
        }
        if (inst instanceof IrInstruction.VirtualCall v) {
            if (v.returnType() == io.jnative.ir.IrType.VOID) {
                return true;
            }
            return !ctx.isPureVirtual(function, v);
        }
        return false;
    }

    private static Set<IrValue.Temp> computeUsed(IrFunction function) {
        Set<IrValue.Temp> used = new HashSet<>();
        for (IrBasicBlock block : function.blocks()) {
            for (IrInstruction inst : block.instructions()) {
                for (IrUtil.ValueUse u : IrUtil.usedValues(inst)) {
                    if (u.value() instanceof IrValue.Temp t) {
                        used.add(t);
                    }
                }
            }
        }
        return used;
    }
}
