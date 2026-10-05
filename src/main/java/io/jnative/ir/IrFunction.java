package io.jnative.ir;

import java.util.ArrayList;
import java.util.List;

public final class IrFunction {
    private final String name;
    private final IrType returnType;
    private final List<IrValue.Param> params;
    private final List<IrBasicBlock> blocks = new ArrayList<>();
    private final boolean isMain;

    public IrFunction(String name, IrType returnType, List<IrValue.Param> params, boolean isMain) {
        this.name = name;
        this.returnType = returnType;
        this.params = List.copyOf(params);
        this.isMain = isMain;
    }

    public String name() {
        return name;
    }

    public IrType returnType() {
        return returnType;
    }

    public List<IrValue.Param> params() {
        return params;
    }

    public List<IrBasicBlock> blocks() {
        return blocks;
    }

    public boolean isMain() {
        return isMain;
    }

    public IrBasicBlock addBlock(String name) {
        IrBasicBlock block = new IrBasicBlock(name);
        blocks.add(block);
        return block;
    }
}
