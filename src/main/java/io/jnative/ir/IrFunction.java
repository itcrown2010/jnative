/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ir;

import java.util.ArrayList;
import java.util.List;

/**
 * @author 张晓达
 */
public final class IrFunction {
    private final String name;
    private final IrType returnType;
    private final List<IrValue.Param> params;
    private final List<IrBasicBlock> blocks = new ArrayList<>();
    private final boolean isMain;
    private boolean omitSafepoints;
    private boolean noGcRegion;
    private boolean isAsync;

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

    public boolean omitSafepoints() {
        return omitSafepoints;
    }

    public void setOmitSafepoints(boolean omitSafepoints) {
        this.omitSafepoints = omitSafepoints;
    }

    public boolean noGcRegion() {
        return noGcRegion;
    }

    public void setNoGcRegion(boolean noGcRegion) {
        this.noGcRegion = noGcRegion;
    }

    public boolean isAsync() {
        return isAsync;
    }

    public void setAsync(boolean isAsync) {
        this.isAsync = isAsync;
    }

    public IrBasicBlock addBlock(String name) {
        IrBasicBlock block = new IrBasicBlock(name);
        blocks.add(block);
        return block;
    }
}
