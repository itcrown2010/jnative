package io.jnative.opt.cfg;

import io.jnative.ir.IrBasicBlock;
import io.jnative.ir.IrFunction;
import io.jnative.ir.IrInstruction;
import io.jnative.ir.IrValue;
import io.jnative.opt.IrUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Def-use chains for temps within one function. */
public final class DefUse {
    private final IrFunction function;
    private final Map<IrValue.Temp, DefSite> definitions = new HashMap<>();
    private final Map<IrValue.Temp, List<UseSite>> uses = new HashMap<>();

    public record DefSite(IrBasicBlock block, int index, IrInstruction instruction) {
    }

    public record UseSite(IrBasicBlock block, int index, IrInstruction instruction, String operand) {
    }

    public DefUse(IrFunction function) {
        this.function = function;
        build();
    }

    public IrFunction function() {
        return function;
    }

    public DefSite definition(IrValue.Temp temp) {
        return definitions.get(temp);
    }

    public List<UseSite> uses(IrValue.Temp temp) {
        return uses.getOrDefault(temp, List.of());
    }

    public Map<IrValue.Temp, DefSite> definitions() {
        return definitions;
    }

    private void build() {
        for (IrBasicBlock block : function.blocks()) {
            List<IrInstruction> insts = block.instructions();
            for (int i = 0; i < insts.size(); i++) {
                IrInstruction inst = insts.get(i);
                IrValue.Temp def = IrUtil.definingTemp(inst);
                if (def != null) {
                    definitions.put(def, new DefSite(block, i, inst));
                }
                for (var use : IrUtil.usedValues(inst)) {
                    if (use.value() instanceof IrValue.Temp t) {
                        uses.computeIfAbsent(t, k -> new ArrayList<>())
                                .add(new UseSite(block, i, inst, use.operand()));
                    }
                }
            }
        }
    }
}
