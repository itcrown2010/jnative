/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.opt;

import io.jnative.ir.IrBasicBlock;
import io.jnative.ir.IrFunction;
import io.jnative.ir.IrInstruction;
import io.jnative.ir.IrValue;
import io.jnative.semantic.ClassSymbol;
import io.jnative.vtable.ITable;
import io.jnative.vtable.VTable;

import java.util.List;

/** Devirtualize monomorphic virtual and interface calls.
 * @author 张晓达
 */
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
                if (inst instanceof IrInstruction.VirtualCall vc) {
                    String target = resolveVirtual(ctx, function, vc);
                    if (target == null) {
                        continue;
                    }
                    block.instructions().set(i, new IrInstruction.Call(
                            vc.dest(), target, vc.returnType(), buildArgs(vc.args(), vc.receiver())));
                    ctx.report().increment("devirtualize");
                    ctx.report().increment("devirtualize.virtual");
                } else if (inst instanceof IrInstruction.InterfaceCall ic) {
                    String target = resolveInterface(ctx, function, ic);
                    if (target == null) {
                        continue;
                    }
                    block.instructions().set(i, new IrInstruction.Call(
                            ic.dest(), target, ic.returnType(), buildArgs(ic.args(), ic.receiver())));
                    ctx.report().increment("devirtualize");
                    ctx.report().increment("devirtualize.interface");
                }
            }
        }
    }

    private String resolveVirtual(OptimizationContext ctx, IrFunction function, IrInstruction.VirtualCall vc) {
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

    private String resolveInterface(OptimizationContext ctx, IrFunction function, IrInstruction.InterfaceCall ic) {
        if (ic.receiver() instanceof IrValue.Temp t) {
            String exact = ctx.exactClass(function, t);
            if (exact == null) {
                exact = IrUtil.traceToNewObjectClass(function, t);
            }
            if (exact == null) {
                exact = IrUtil.traceToMakeCallableClass(function, t);
            }
            if (exact != null) {
                String mangled = lookupITable(ctx, exact, ic.interfaceId(), ic.slot());
                if (mangled != null) {
                    return mangled;
                }
            }
        }
        // Closed-world: single implementor of this interface method.
        return ctx.singleInterfaceImplementation(ic.interfaceId(), ic.slot());
    }

    private static String lookupITable(OptimizationContext ctx, String className, int ifaceId, int slot) {
        ClassSymbol cls = ctx.typed().classes().get(className);
        if (cls == null) {
            return null;
        }
        for (ITable it : cls.itables()) {
            if (it.interfaceSymbol().interfaceId() == ifaceId) {
                if (slot >= 0 && slot < it.size()) {
                    return it.entries().get(slot).mangledImplementation();
                }
            }
        }
        // Also search module itables by global name prefix.
        for (ITable it : ctx.module().itables().values()) {
            if (it.globalName().startsWith("itable." + className + ".")
                    && it.interfaceSymbol().interfaceId() == ifaceId
                    && slot >= 0 && slot < it.size()) {
                return it.entries().get(slot).mangledImplementation();
            }
        }
        return null;
    }

    private List<IrValue> buildArgs(List<IrValue> args, IrValue receiver) {
        if (args != null && !args.isEmpty()) {
            return args;
        }
        return List.of(receiver);
    }
}
