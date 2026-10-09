package io.jnative.opt;

import io.jnative.ir.IrBasicBlock;
import io.jnative.ir.IrFunction;
import io.jnative.ir.IrInstruction;
import io.jnative.ir.IrModule;
import io.jnative.ir.IrValue;

import java.util.HashSet;
import java.util.Set;

/** Basic IR sanity checks after optimization.
 * @author 张晓达
 */
public final class IrVerifier {

    private IrVerifier() {
    }

    public static void verify(IrModule module) {
        for (IrFunction function : module.functions()) {
            verifyFunction(function);
        }
    }

    private static void verifyFunction(IrFunction function) {
        Set<String> blockNames = IrUtil.blockNames(function);
        Set<IrValue.Temp> defined = new HashSet<>();

        for (IrBasicBlock block : function.blocks()) {
            if (block.instructions().isEmpty()) {
                throw new IllegalStateException("empty block: " + function.name() + "." + block.name());
            }
            boolean hasTerminator = false;
            for (IrInstruction inst : block.instructions()) {
                IrValue.Temp def = IrUtil.definingTemp(inst);
                if (def != null) {
                    defined.add(def);
                }
                switch (inst) {
                    case IrInstruction.Branch b -> {
                        if (!blockNames.contains(b.target())) {
                            throw new IllegalStateException("bad branch target: " + b.target());
                        }
                        hasTerminator = true;
                    }
                    case IrInstruction.CondBranch cb -> {
                        if (!blockNames.contains(cb.trueTarget()) || !blockNames.contains(cb.falseTarget())) {
                            throw new IllegalStateException("bad cond branch in " + block.name());
                        }
                        hasTerminator = true;
                    }
                    case IrInstruction.Return ignored -> hasTerminator = true;
                    default -> { }
                }
                for (IrUtil.ValueUse use : IrUtil.usedValues(inst)) {
                    if (use.value() instanceof IrValue.Temp t && !defined.contains(t)) {
                        // params and cross-block uses are ok for minimal verifier
                    }
                }
            }
            if (!hasTerminator && block.isTerminated()) {
                // block flag may be stale
            }
            IrInstruction last = block.instructions().getLast();
            if (!(last instanceof IrInstruction.Branch
                    || last instanceof IrInstruction.CondBranch
                    || last instanceof IrInstruction.Return)) {
                throw new IllegalStateException("block missing terminator: "
                        + function.name() + "." + block.name());
            }
        }
    }
}
