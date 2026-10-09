package io.jnative.opt;

import io.jnative.ir.IrBasicBlock;
import io.jnative.ir.IrFunction;
import io.jnative.ir.IrInstruction;
import io.jnative.ir.IrType;
import io.jnative.ir.IrValue;
import io.jnative.semantic.FunctionEffects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Inline small pure/deterministic single-block callees at call sites. */
public final class InlinePass implements OptimizationPass {
    private static final int MAX_INSTRUCTIONS = 20;
    private int inlineSeq;

    @Override
    public String name() {
        return "inline";
    }

    @Override
    public void run(OptimizationContext ctx) {
        inlineSeq = 0;
        for (IrFunction function : new ArrayList<>(ctx.module().functions())) {
            inlineInFunction(ctx, function);
        }
    }

    private void inlineInFunction(OptimizationContext ctx, IrFunction caller) {
        if (caller.isMain()) {
            return;
        }
        for (IrBasicBlock block : caller.blocks()) {
            for (int i = 0; i < block.instructions().size(); i++) {
                if (!ctx.canInline()) {
                    return;
                }
                IrInstruction inst = block.instructions().get(i);
                if (!(inst instanceof IrInstruction.Call call) || call.dest() == null) {
                    continue;
                }
                IrFunction callee = ctx.module().findFunction(call.function());
                if (callee == null || callee.isMain() || callee.name().equals(caller.name())) {
                    continue;
                }
                // Single-block only — multi-block inlining needs SSA/phi support.
                if (callee.blocks().size() != 1 || hasControlFlow(callee) || hasFrameAlloc(callee)) {
                    continue;
                }
                if (IrUtil.instructionCount(callee) > MAX_INSTRUCTIONS) {
                    continue;
                }
                FunctionEffects fx = ctx.effects(call.function());
                // Only inline pure callees. Deterministic-but-impure (e.g. WRITES_GLOBAL)
                // must not be inlined until static load/store remap is fully proven in all
                // shapes — incorrect remap previously collided with caller temps (%t0).
                if (!fx.isPure()) {
                    continue;
                }
                if (calleeAllocates(callee)) {
                    continue;
                }
                if (!inlineCallSite(ctx, caller, block, i, call, callee)) {
                    continue;
                }
                ctx.consumeInline();
                ctx.report().increment("inline");
                ctx.invalidateFunction(caller);
                return;
            }
        }
    }

    private boolean inlineCallSite(
            OptimizationContext ctx,
            IrFunction caller,
            IrBasicBlock block,
            int callIndex,
            IrInstruction.Call call,
            IrFunction callee
    ) {
        int seq = ++inlineSeq;
        Map<IrValue, IrValue> remap = new HashMap<>();
        for (int p = 0; p < callee.params().size() && p < call.args().size(); p++) {
            remap.put(callee.params().get(p), call.args().get(p));
        }

        List<IrInstruction> inlined = new ArrayList<>();
        IrValue returnValue = null;
        for (IrBasicBlock cb : callee.blocks()) {
            for (IrInstruction inst : cb.instructions()) {
                if (inst instanceof IrInstruction.Return r) {
                    returnValue = r.value() == null ? null : remapValue(ctx, r.value(), remap, seq);
                    break;
                }
                inlined.add(remapInstruction(ctx, inst, remap, seq));
            }
        }

        block.instructions().remove(callIndex);
        block.instructions().addAll(callIndex, inlined);
        if (returnValue != null && call.dest() != null) {
            block.instructions().add(callIndex + inlined.size(),
                    new IrInstruction.Cast(call.dest(), returnValue, call.returnType()));
        } else if (call.dest() != null) {
            block.instructions().add(callIndex + inlined.size(),
                    new IrInstruction.Binary(call.dest(), IrInstruction.BinOp.ADD,
                            new IrValue.Const(IrType.I32, 0), new IrValue.Const(IrType.I32, 0)));
        }
        block.recalcTerminated();
        return true;
    }

    private static boolean calleeAllocates(IrFunction callee) {
        for (IrBasicBlock block : callee.blocks()) {
            for (IrInstruction inst : block.instructions()) {
                if (inst instanceof IrInstruction.NewObject
                        || inst instanceof IrInstruction.NewArray
                        || inst instanceof IrInstruction.MakeCallable) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasControlFlow(IrFunction callee) {
        for (IrBasicBlock block : callee.blocks()) {
            for (IrInstruction inst : block.instructions()) {
                if (inst instanceof IrInstruction.Branch
                        || inst instanceof IrInstruction.CondBranch
                        || inst instanceof IrInstruction.JumpLabel) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Inlined allocas mid-block break entry-frame GC root dominance in LLVM emission. */
    private static boolean hasFrameAlloc(IrFunction callee) {
        for (IrBasicBlock block : callee.blocks()) {
            for (IrInstruction inst : block.instructions()) {
                if (inst instanceof IrInstruction.Alloca
                        || inst instanceof IrInstruction.StackAlloc
                        || (inst instanceof IrInstruction.NewObject n && n.stackAllocated())) {
                    return true;
                }
            }
        }
        return false;
    }

    private IrInstruction remapInstruction(
            OptimizationContext ctx,
            IrInstruction inst,
            Map<IrValue, IrValue> remap,
            int seq
    ) {
        if (inst instanceof IrInstruction.Alloca a) {
            IrValue.Local fresh = new IrValue.Local(a.dest().identifier() + "_inl" + seq, a.dest().type());
            remap.put(a.dest(), fresh);
            return new IrInstruction.Alloca(fresh);
        }
        if (inst instanceof IrInstruction.StackAlloc s) {
            IrValue.Local fresh = new IrValue.Local(s.dest().identifier() + "_inl" + seq, s.dest().type());
            remap.put(s.dest(), fresh);
            return new IrInstruction.StackAlloc(
                    fresh, s.structName(), s.llvmTypeName(), s.alignment(), s.sizeBytes());
        }
        // Ensure defining temps get fresh identities before mapping operands.
        IrValue.Temp def = IrUtil.definingTemp(inst);
        if (def != null && !remap.containsKey(def)) {
            remap.put(def, ctx.freshTemp(def.type()));
        }
        return IrUtil.mapValues(inst, v -> remapValue(ctx, v, remap, seq));
    }

    private IrValue remapValue(
            OptimizationContext ctx,
            IrValue v,
            Map<IrValue, IrValue> remap,
            int seq
    ) {
        if (v == null) {
            return null;
        }
        IrValue mapped = remap.get(v);
        if (mapped != null) {
            return mapped;
        }
        if (v instanceof IrValue.Temp t) {
            IrValue.Temp fresh = ctx.freshTemp(t.type());
            remap.put(t, fresh);
            return fresh;
        }
        if (v instanceof IrValue.Local l) {
            IrValue.Local fresh = new IrValue.Local(l.identifier() + "_inl" + seq, l.type());
            remap.put(l, fresh);
            return fresh;
        }
        return v;
    }
}
