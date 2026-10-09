package io.jnative.ir;

import java.util.ArrayList;
import java.util.List;

/**
 * @author 张晓达
 */
public final class IrBasicBlock {
    private final String name;
    private final List<IrInstruction> instructions = new ArrayList<>();
    private boolean terminated;

    public IrBasicBlock(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public List<IrInstruction> instructions() {
        return instructions;
    }

    public boolean isTerminated() {
        return terminated;
    }

    public void add(IrInstruction instruction) {
        if (terminated) {
            return;
        }
        instructions.add(instruction);
        markTerminator(instruction);
    }

    public void setTerminated(boolean terminated) {
        this.terminated = terminated;
    }

    public void recalcTerminated() {
        terminated = false;
        for (IrInstruction inst : instructions) {
            markTerminator(inst);
            if (terminated) {
                break;
            }
        }
    }

    private void markTerminator(IrInstruction instruction) {
        if (instruction instanceof IrInstruction.Branch
                || instruction instanceof IrInstruction.CondBranch
                || instruction instanceof IrInstruction.Return) {
            terminated = true;
        }
    }
}
