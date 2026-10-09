package io.jnative.opt;

import io.jnative.ir.IrBasicBlock;
import io.jnative.ir.IrFunction;
import io.jnative.ir.IrInstruction;
import io.jnative.ir.IrModule;
import io.jnative.ir.IrValue;
import io.jnative.layout.ClassLayout;
import io.jnative.layout.FieldLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Scalar-replace non-escaping primitive-only objects with constructor argument values.
 * @author 张晓达
 */
public final class ScalarReplacementPass implements OptimizationPass {
    @Override
    public String name() {
        return "scalar-replacement";
    }

    @Override
    public void run(OptimizationContext ctx) {
        for (IrFunction function : ctx.module().functions()) {
            replaceInFunction(ctx, function);
            ctx.invalidateFunction(function);
        }
    }

    private void replaceInFunction(OptimizationContext ctx, IrFunction function) {
        var escape = ctx.escapeStates(function);
        List<Candidate> candidates = new ArrayList<>();
        for (IrBasicBlock block : function.blocks()) {
            for (int i = 0; i < block.instructions().size(); i++) {
                if (block.instructions().get(i) instanceof IrInstruction.NewObject n) {
                    EscapeState st = escape.get(n.dest());
                    if (st != null && st.isNoEscape()) {
                        candidates.add(new Candidate(block, i, n));
                    }
                }
            }
        }
        for (Candidate cand : candidates) {
            tryReplace(ctx, function, cand, escape);
        }
    }

    private void tryReplace(
            OptimizationContext ctx,
            IrFunction function,
            Candidate cand,
            Map<IrValue.Temp, EscapeState> escape
    ) {
        IrInstruction.NewObject n = cand.newObject();
        IrValue.Temp root = n.dest();
        Set<IrValue.Temp> aliases = collectAliases(function, root);
        aliases.add(root);

        ClassLayout layout = ctx.module().classLayouts().get(n.className());
        if (layout == null || !allPrimitiveFields(layout)) {
            return;
        }

        IrInstruction.ConstructorCall ctorCall = findConstructorCall(function, aliases);
        if (ctorCall == null || !onlyFieldUses(function, aliases, ctorCall)) {
            return;
        }
        if (!hasLoadFieldUse(function, aliases)) {
            return;
        }

        Map<Integer, IrValue> fieldValues = mapConstructorFields(ctx.module(), ctorCall, layout);
        if (fieldValues == null) {
            fieldValues = positionalFieldMap(ctorCall, layout);
        }
        if (fieldValues == null) {
            return;
        }

        for (IrBasicBlock block : function.blocks()) {
            List<IrInstruction> insts = block.instructions();
            for (int i = 0; i < insts.size(); i++) {
                IrInstruction inst = insts.get(i);
                if (inst instanceof IrInstruction.LoadField lf && isAlias(lf.receiver(), aliases)) {
                    IrValue val = fieldValues.get(lf.fieldIndex());
                    if (val != null) {
                        insts.set(i, assignTemp(lf.dest(), val, lf.fieldType()));
                    }
                } else if (inst instanceof IrInstruction.StoreField sf && isAlias(sf.receiver(), aliases)) {
                    fieldValues.put(sf.fieldIndex(), sf.value());
                    insts.remove(i);
                    i--;
                } else if (inst instanceof IrInstruction.NullCheck nc && isAlias(nc.pointer(), aliases)) {
                    insts.remove(i);
                    i--;
                }
            }
            block.recalcTerminated();
        }

        removeNewObjectChain(function, cand, aliases, ctorCall);
        for (IrValue.Temp a : aliases) {
            escape.remove(a);
        }
        ctx.report().increment("scalar-replacement");
        ctx.report().note("scalar-replaced " + n.className() + " in " + function.name());
    }

    private static Set<IrValue.Temp> collectAliases(IrFunction function, IrValue.Temp root) {
        Set<IrValue.Temp> aliases = new HashSet<>();
        Set<IrValue.Local> aliasLocals = new HashSet<>();
        aliases.add(root);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (IrBasicBlock block : function.blocks()) {
                for (IrInstruction inst : block.instructions()) {
                    if (inst instanceof IrInstruction.Store s && s.dest() instanceof IrValue.Local local) {
                        if (isAlias(s.value(), aliases)) {
                            if (aliasLocals.add(local)) {
                                changed = true;
                            }
                        }
                    }
                    if (inst instanceof IrInstruction.Load l && l.source() instanceof IrValue.Local local) {
                        if (aliasLocals.contains(local) && aliases.add(l.dest())) {
                            changed = true;
                        }
                    }
                }
            }
        }
        return aliases;
    }

    private static boolean isAlias(IrValue value, Set<IrValue.Temp> aliases) {
        return value instanceof IrValue.Temp t && aliases.contains(t);
    }

    private static IrInstruction assignTemp(IrValue.Temp dest, IrValue src, io.jnative.ir.IrType fieldType) {
        if (src instanceof IrValue.Const c && c.type() == fieldType) {
            return new IrInstruction.Binary(dest, IrInstruction.BinOp.ADD, c,
                    new IrValue.Const(fieldType, 0));
        }
        return new IrInstruction.Cast(dest, src, fieldType);
    }

    private static boolean allPrimitiveFields(ClassLayout layout) {
        for (FieldLayout f : layout.fields()) {
            if (f.isReference()) {
                return false;
            }
        }
        return true;
    }

    private IrInstruction.ConstructorCall findConstructorCall(IrFunction function, Set<IrValue.Temp> aliases) {
        for (IrBasicBlock block : function.blocks()) {
            for (IrInstruction inst : block.instructions()) {
                if (inst instanceof IrInstruction.ConstructorCall cc
                        && !cc.args().isEmpty()
                        && isAlias(cc.args().getFirst(), aliases)) {
                    return cc;
                }
            }
        }
        return null;
    }

    private boolean hasLoadFieldUse(IrFunction function, Set<IrValue.Temp> aliases) {
        for (IrBasicBlock block : function.blocks()) {
            for (IrInstruction inst : block.instructions()) {
                if (inst instanceof IrInstruction.LoadField lf && isAlias(lf.receiver(), aliases)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean onlyFieldUses(
            IrFunction function,
            Set<IrValue.Temp> aliases,
            IrInstruction.ConstructorCall ctor
    ) {
        for (IrBasicBlock block : function.blocks()) {
            for (IrInstruction inst : block.instructions()) {
                if (inst instanceof IrInstruction.LoadField lf && isAlias(lf.receiver(), aliases)) {
                    continue;
                }
                if (inst instanceof IrInstruction.StoreField sf && isAlias(sf.receiver(), aliases)) {
                    continue;
                }
                if (inst instanceof IrInstruction.ConstructorCall cc && cc == ctor) {
                    continue;
                }
                if (inst instanceof IrInstruction.NewObject no && aliases.contains(no.dest())) {
                    continue;
                }
                if (inst instanceof IrInstruction.NullCheck nc && isAlias(nc.pointer(), aliases)) {
                    continue;
                }
                if (inst instanceof IrInstruction.Store s && isAlias(s.value(), aliases)) {
                    continue;
                }
                if (inst instanceof IrInstruction.Load l && isAlias(l.dest(), aliases)) {
                    continue;
                }
                for (IrUtil.ValueUse u : IrUtil.usedValues(inst)) {
                    if (isAlias(u.value(), aliases)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private Map<Integer, IrValue> positionalFieldMap(IrInstruction.ConstructorCall cc, ClassLayout layout) {
        if (cc.args().size() != layout.fields().size() + 1) {
            return null;
        }
        Map<Integer, IrValue> map = new HashMap<>();
        for (int i = 0; i < layout.fields().size(); i++) {
            map.put(layout.fields().get(i).index(), cc.args().get(i + 1));
        }
        return map;
    }

    private Map<Integer, IrValue> mapConstructorFields(
            IrModule module,
            IrInstruction.ConstructorCall cc,
            ClassLayout layout
    ) {
        IrFunction ctorFn = module.findFunction(cc.mangledName());
        if (ctorFn == null) {
            return null;
        }
        Map<Integer, IrValue> fromBody = new HashMap<>();
        for (IrBasicBlock block : ctorFn.blocks()) {
            for (IrInstruction inst : block.instructions()) {
                if (inst instanceof IrInstruction.StoreField sf) {
                    fromBody.put(sf.fieldIndex(), resolveCtorValue(sf.value(), ctorFn, cc));
                }
            }
        }
        if (fromBody.size() != layout.fields().size()) {
            return null;
        }
        return fromBody;
    }

    private IrValue resolveCtorValue(IrValue value, IrFunction ctorFn, IrInstruction.ConstructorCall cc) {
        if (value instanceof IrValue.Param p) {
            int idx = indexOfParam(ctorFn, p.identifier());
            if (idx >= 0 && idx < cc.args().size()) {
                return cc.args().get(idx);
            }
        }
        if (value instanceof IrValue.Temp t) {
            DefUseChain chain = traceLoad(ctorFn, t);
            if (chain != null) {
                return resolveCtorValue(chain.source(), ctorFn, cc);
            }
        }
        return value;
    }

    private int indexOfParam(IrFunction fn, String name) {
        for (int i = 0; i < fn.params().size(); i++) {
            if (fn.params().get(i).identifier().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private record DefUseChain(IrValue source) {
    }

    private DefUseChain traceLoad(IrFunction fn, IrValue.Temp temp) {
        for (IrBasicBlock block : fn.blocks()) {
            for (IrInstruction inst : block.instructions()) {
                if (inst instanceof IrInstruction.Load l && l.dest().equals(temp)) {
                    if (l.source() instanceof IrValue.Local local) {
                        return traceLocalStore(fn, local);
                    }
                }
            }
        }
        return null;
    }

    private DefUseChain traceLocalStore(IrFunction fn, IrValue.Local local) {
        for (IrBasicBlock block : fn.blocks()) {
            for (IrInstruction inst : block.instructions()) {
                if (inst instanceof IrInstruction.Store s && s.dest().equals(local)) {
                    return new DefUseChain(s.value());
                }
            }
        }
        return null;
    }

    private void removeNewObjectChain(
            IrFunction function,
            Candidate cand,
            Set<IrValue.Temp> aliases,
            IrInstruction.ConstructorCall ctorCall
    ) {
        for (IrBasicBlock block : function.blocks()) {
            block.instructions().removeIf(inst -> {
                if (inst == ctorCall) {
                    return true;
                }
                if (inst instanceof IrInstruction.NewObject no && aliases.contains(no.dest())) {
                    return true;
                }
                if (inst instanceof IrInstruction.Store s && isAlias(s.value(), aliases)) {
                    return true;
                }
                if (inst instanceof IrInstruction.Load l && isAlias(l.dest(), aliases)) {
                    return true;
                }
                return false;
            });
            block.recalcTerminated();
        }
    }

    private void removeInstruction(IrBasicBlock block, int index) {
        if (index >= 0 && index < block.instructions().size()) {
            block.instructions().remove(index);
            block.recalcTerminated();
        }
    }

    private record Candidate(IrBasicBlock block, int index, IrInstruction.NewObject newObject) {
    }
}
