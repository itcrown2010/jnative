/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.opt;

import io.jnative.ir.IrFunction;
import io.jnative.ir.IrInstruction;
import io.jnative.vtable.VTable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/** Remove functions not reachable from entry points (conservative w.r.t. vtables).
 * @author 张晓达
 */
public final class WholeProgramDcePass implements OptimizationPass {
    @Override
    public String name() {
        return "whole-program-dce";
    }

    @Override
    public void run(OptimizationContext ctx) {
        Set<String> reachable = new HashSet<>(ctx.protectedFunctionNames());
        reachable.addAll(allVtableImplementations(ctx));
        // Bridges (boxing adapters for erased generic interface slots) are only referenced from itables.
        for (var itable : ctx.module().itables().values()) {
            for (var e : itable.entries()) {
                if (e.bridge() != null) {
                    reachable.add(e.bridge());
                }
            }
        }
        for (IrFunction f : ctx.module().functions()) {
            String n = f.name();
            if (n.contains("_lambda_") || n.contains("_thunk_")
                    || n.startsWith("jnative_cb_trampoline_")
                    || n.endsWith("__async_resume")) {
                reachable.add(n);
            }
        }
        for (var block : ctx.module().functions()) {
            for (var bb : block.blocks()) {
                for (IrInstruction inst : bb.instructions()) {
                    if (inst instanceof IrInstruction.MakeCallable mc) {
                        reachable.add(mc.codeSymbol());
                    }
                    if (inst instanceof IrInstruction.CreateNativeCallback c) {
                        reachable.add(c.trampolineSymbol());
                    }
                }
            }
        }
        Queue<String> work = new ArrayDeque<>(reachable);

        while (!work.isEmpty()) {
            String name = work.poll();
            IrFunction fn = ctx.module().findFunction(name);
            if (fn == null) {
                continue;
            }
            for (var block : fn.blocks()) {
                for (IrInstruction inst : block.instructions()) {
                    switch (inst) {
                        case IrInstruction.Call c -> enqueue(reachable, work, c.function());
                        case IrInstruction.ConstructorCall cc -> enqueue(reachable, work, cc.mangledName());
                        case IrInstruction.VirtualCall v -> enqueueVirtual(ctx, fn, reachable, work, v);
                        default -> { }
                    }
                }
            }
        }

        for (var init : ctx.module().staticInits()) {
            if (init.initFunction() != null) {
                reachable.add(init.initFunction());
            }
        }

        Set<String> remove = new HashSet<>();
        for (IrFunction f : ctx.module().functions()) {
            if (!reachable.contains(f.name())) {
                remove.add(f.name());
            }
        }
        if (!remove.isEmpty()) {
            ctx.module().removeFunctions(remove);
            ctx.report().add("wpdce-remove", remove.size());
        }
    }

    private static Set<String> allVtableImplementations(OptimizationContext ctx) {
        Set<String> impls = new HashSet<>();
        for (VTable vt : ctx.module().vtables().values()) {
            vt.entries().forEach(e -> impls.add(e.mangledImplementation()));
        }
        return impls;
    }

    private static void enqueueVirtual(
            OptimizationContext ctx,
            IrFunction fn,
            Set<String> reachable,
            Queue<String> work,
            IrInstruction.VirtualCall v
    ) {
        String methodName = null;
        if (v.debugName() != null && v.debugName().contains(".")) {
            methodName = v.debugName().substring(v.debugName().indexOf('.') + 1);
        }
        if (v.receiver() instanceof io.jnative.ir.IrValue.Temp t) {
            String exact = IrUtil.traceToNewObjectClass(fn, t);
            if (exact != null) {
                var vt = ctx.module().vtables().get(exact);
                if (vt != null && v.slot() < vt.size()) {
                    enqueue(reachable, work, vt.entries().get(v.slot()).mangledImplementation());
                    return;
                }
            }
        }
        if (methodName != null) {
            String impl = ctx.singleImplementationForMethod(v.slot(), methodName);
            if (impl != null) {
                enqueue(reachable, work, impl);
            }
        }
    }

    private static void enqueue(Set<String> reachable, Queue<String> work, String name) {
        if (name != null && reachable.add(name)) {
            work.add(name);
        }
    }
}
