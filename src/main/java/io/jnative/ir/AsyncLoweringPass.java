/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ir;

import io.jnative.compiler.CompilerOptions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lowers async IR into a cooperative suspend/resume state machine.
 *
 * <p>Each invocation owns an {@code AsyncFrame} and a distinct result {@code Future}.
 * Incomplete await registers a continuation and returns the <em>method</em> result Future
 * (never the awaited Future). Completion schedules {@code name$async_resume(frame)}.
 * @author 张晓达
 */
public final class AsyncLoweringPass {

    private int tempId;
    private int blockId;
    private IrModule module;
    private final Map<Integer, IrValue.Temp> resumeDests = new LinkedHashMap<>();
    private final Map<Integer, IrType> resumeTypes = new LinkedHashMap<>();

    public void run(IrModule module, CompilerOptions options) {
        this.module = module;
        List<IrFunction> snapshot = new ArrayList<>(module.functions());
        for (IrFunction function : snapshot) {
            if (containsAsyncOps(function)) {
                function.setAsync(true);
                resumeDests.clear();
                resumeTypes.clear();
                lowerAsyncFunction(function);
            }
        }
    }

    private static boolean containsAsyncOps(IrFunction function) {
        for (IrBasicBlock block : function.blocks()) {
            for (IrInstruction inst : block.instructions()) {
                if (inst instanceof IrInstruction.AsyncBegin
                        || inst instanceof IrInstruction.Await
                        || inst instanceof IrInstruction.AsyncComplete) {
                    return true;
                }
            }
        }
        return false;
    }

    private void lowerAsyncFunction(IrFunction function) {
        tempId = 10_000; // avoid clashing with IrGenerator temps
        blockId = 0;
        resumeCont.clear();
        awaitSlots.clear();

        // Create resume thunk up-front so fnptr constants always resolve.
        ensureResumeThunkStub(function);

        List<IrValue.Local> allocas = collectAllocas(function);
        Map<Integer, String> resumeLabels = new LinkedHashMap<>();
        IrValue frame = null;

        // Snapshot: expandAwait/addBlock mutates function.blocks().
        List<IrBasicBlock> blocks = new ArrayList<>(function.blocks());
        for (IrBasicBlock block : blocks) {
            List<IrInstruction> out = new ArrayList<>();
            // Snapshot: ensureEntryAlloca may insert into entry while we scan it.
            List<IrInstruction> insts = new ArrayList<>(block.instructions());
            for (IrInstruction inst : insts) {
                switch (inst) {
                    case IrInstruction.AsyncBegin b -> frame = expandBegin(out, b);
                    case IrInstruction.Await a -> {
                        if (frame == null) {
                            throw new IllegalStateException("Await before AsyncBegin in " + function.name());
                        }
                        resumeLabels.put(a.resumeState(), a.resumeLabel());
                        expandAwait(function, block, out, a, frame, allocas);
                    }
                    case IrInstruction.AsyncResume r -> {
                        // Slow-path re-entry only: restore spills and load await outcome into slot.
                        emitRestore(out, frame, allocas);
                        emitAwaitOutcomeToSlot(out, awaitSlots.get(r.resumeState()), frame, r.resumeState());
                        String cont = resumeCont.getOrDefault(block.name(), block.name() + ".cont");
                        out.add(new IrInstruction.Branch(cont));
                    }
                    case IrInstruction.AsyncComplete c -> {
                        if (frame == null) {
                            throw new IllegalStateException("AsyncComplete before AsyncBegin");
                        }
                        out.add(call(null, "jnative_async_complete", IrType.VOID,
                                List.of(frame, c.value())));
                        IrValue.Temp rf = temp(IrType.PTR);
                        out.add(call(rf, "jnative_async_result_future", IrType.PTR, List.of(frame)));
                        out.add(new IrInstruction.Return(rf));
                    }
                    case IrInstruction.AsyncFail f -> {
                        if (frame != null) {
                            out.add(call(null, "jnative_async_complete_exception", IrType.VOID,
                                    List.of(frame, f.exception())));
                            IrValue.Temp rf = temp(IrType.PTR);
                            out.add(call(rf, "jnative_async_result_future", IrType.PTR, List.of(frame)));
                            out.add(new IrInstruction.Return(rf));
                        } else {
                            out.add(call(null, "jnative_async_fail", IrType.VOID, List.of(f.exception())));
                            out.add(new IrInstruction.Return(new IrValue.Const(IrType.PTR, "null")));
                        }
                    }
                    case IrInstruction.AsyncSuspend s -> out.add(call(null, "jnative_async_suspend",
                            IrType.VOID, List.of(s.frame(),
                                    new IrValue.Const(IrType.I32, String.valueOf(s.resumeState())),
                                    s.future())));
                    default -> out.add(inst);
                }
            }
            // Move user code after AsyncResume into the pre-created .cont block.
            String contName = resumeCont.get(block.name());
            if (contName != null && out.stream().anyMatch(i ->
                    i instanceof IrInstruction.Branch b && contName.equals(b.target()))) {
                int branchAt = -1;
                for (int i = 0; i < out.size(); i++) {
                    if (out.get(i) instanceof IrInstruction.Branch b && contName.equals(b.target())) {
                        branchAt = i;
                        break;
                    }
                }
                List<IrInstruction> head = new ArrayList<>(out.subList(0, branchAt + 1));
                List<IrInstruction> rest = new ArrayList<>(out.subList(branchAt + 1, out.size()));
                block.instructions().clear();
                block.instructions().addAll(head);
                block.recalcTerminated();
                IrBasicBlock cont = findBlock(function, contName);
                if (cont != null) {
                    cont.instructions().clear();
                    cont.instructions().add(new IrInstruction.JumpLabel(cont.name()));
                    // Define await dest once at the join.
                    Integer stateForBlock = null;
                    for (Map.Entry<Integer, String> e : resumeLabels.entrySet()) {
                        if (e.getValue().equals(block.name())) {
                            stateForBlock = e.getKey();
                            break;
                        }
                    }
                    if (stateForBlock != null && resumeDests.get(stateForBlock) != null
                            && awaitSlots.get(stateForBlock) != null) {
                        cont.instructions().add(new IrInstruction.Load(
                                resumeDests.get(stateForBlock), awaitSlots.get(stateForBlock)));
                    }
                    cont.instructions().addAll(rest);
                    cont.recalcTerminated();
                }
            } else {
                block.instructions().clear();
                block.instructions().addAll(out);
                block.recalcTerminated();
            }
        }

        if (frame == null) {
            return;
        }
        // Re-apply await spill allocas — block rewrites may have dropped mid-pass inserts.
        for (IrValue.Local slot : awaitSlots.values()) {
            ensureEntryAlloca(function, slot);
        }
        insertStateDispatch(function, frame, resumeLabels, allocas);
        for (IrValue.Local slot : awaitSlots.values()) {
            ensureEntryAlloca(function, slot);
        }
        fillResumeThunk(function);
    }

    private void ensureResumeThunkStub(IrFunction function) {
        String thunkName = resumeThunkName(function);
        for (IrFunction f : module.functions()) {
            if (f.name().equals(thunkName)) {
                return;
            }
        }
        IrFunction thunk = new IrFunction(
                thunkName, IrType.VOID, List.of(new IrValue.Param("frame", IrType.PTR)), false);
        IrBasicBlock entry = thunk.addBlock("entry");
        entry.instructions().add(new IrInstruction.JumpLabel("entry"));
        entry.instructions().add(new IrInstruction.Return());
        entry.recalcTerminated();
        module.functions().add(thunk);
    }

    private final Map<String, String> resumeCont = new LinkedHashMap<>();
    private final Map<Integer, IrValue.Local> awaitSlots = new LinkedHashMap<>();

    private static void ensureEntryAlloca(IrFunction function, IrValue.Local slot) {
        IrBasicBlock entry = function.blocks().get(0);
        for (IrInstruction inst : entry.instructions()) {
            if (inst instanceof IrInstruction.Alloca a && a.dest() == slot) {
                return;
            }
        }
        // Insert after existing allocas at the front of entry.
        int idx = 0;
        while (idx < entry.instructions().size()
                && entry.instructions().get(idx) instanceof IrInstruction.Alloca) {
            idx++;
        }
        entry.instructions().add(idx, new IrInstruction.Alloca(slot));
    }

    private static IrBasicBlock findBlock(IrFunction function, String name) {
        for (IrBasicBlock b : function.blocks()) {
            if (b.name().equals(name)) {
                return b;
            }
        }
        return null;
    }

    private IrValue expandBegin(List<IrInstruction> out, IrInstruction.AsyncBegin b) {
        IrValue.Temp existing = temp(IrType.PTR);
        out.add(call(existing, "jnative_async_take_resume", IrType.PTR, List.of()));
        IrValue.Temp begun = temp(IrType.PTR);
        out.add(call(begun, "jnative_async_begin", IrType.PTR, List.of()));
        IrValue.Temp frame = b.frame();
        out.add(call(frame, "jnative_async_select_nonzero", IrType.PTR, List.of(existing, begun)));
        return frame;
    }

    private void expandAwait(
            IrFunction function,
            IrBasicBlock containingBlock,
            List<IrInstruction> out,
            IrInstruction.Await a,
            IrValue frame,
            List<IrValue.Local> allocas
    ) {
        resumeDests.put(a.resumeState(), a.dest());
        resumeTypes.put(a.resumeState(), a.resultType() == null ? IrType.PTR : a.resultType());

        IrBasicBlock fast = function.addBlock("await." + a.resumeState() + ".fast." + blockId++);
        IrBasicBlock slow = function.addBlock("await." + a.resumeState() + ".slow." + blockId++);
        IrBasicBlock fastOk = function.addBlock("await." + a.resumeState() + ".ok." + blockId++);
        IrBasicBlock fastFail = function.addBlock("await." + a.resumeState() + ".fail." + blockId++);
        String resumeLabel = a.resumeLabel();
        // Join point after await result is available (fast path skips restore/outcome).
        IrBasicBlock cont = function.addBlock(resumeLabel + ".cont");
        resumeCont.put(resumeLabel, cont.name());
        // SSA: dest must be defined once — spill through an alloca at the join.
        IrType rt = resumeTypes.get(a.resumeState());
        IrValue.Local slot = new IrValue.Local("await.slot." + a.resumeState(), rt);
        awaitSlots.put(a.resumeState(), slot);
        // Alloca must live in entry — emit via helper on function entry later; for now add to entry.
        ensureEntryAlloca(function, slot);

        IrValue.Temp done = temp(IrType.I1);
        out.add(call(done, "jnative_future_isComplete", IrType.I1, List.of(a.future())));
        out.add(new IrInstruction.CondBranch(done, fast.name(), slow.name()));

        fast.instructions().add(new IrInstruction.JumpLabel(fast.name()));
        IrValue.Temp st = temp(IrType.I32);
        fast.instructions().add(call(st, "jnative_future_get_state", IrType.I32, List.of(a.future())));
        IrValue.Temp isOk = temp(IrType.I1);
        fast.instructions().add(new IrInstruction.Compare(isOk, IrInstruction.CmpOp.EQ,
                st, new IrValue.Const(IrType.I32, "1")));
        fast.instructions().add(new IrInstruction.CondBranch(isOk, fastOk.name(), fastFail.name()));

        fastOk.instructions().add(new IrInstruction.JumpLabel(fastOk.name()));
        emitLoadFutureResultToSlot(fastOk.instructions(), slot, a.future(), rt);
        fastOk.instructions().add(new IrInstruction.Branch(cont.name()));

        fastFail.instructions().add(new IrInstruction.JumpLabel(fastFail.name()));
        IrValue.Temp err = temp(IrType.PTR);
        fastFail.instructions().add(call(err, "jnative_future_getError", IrType.PTR, List.of(a.future())));
        // Propagates failure into the method Future (throws if err non-null for catch support later).
        fastFail.instructions().add(call(null, "jnative_async_fail_await", IrType.VOID,
                List.of(frame, err)));
        IrValue.Temp rfFail = temp(IrType.PTR);
        fastFail.instructions().add(call(rfFail, "jnative_async_result_future", IrType.PTR, List.of(frame)));
        fastFail.instructions().add(new IrInstruction.Return(rfFail));

        slow.instructions().add(new IrInstruction.JumpLabel(slow.name()));
        emitSave(slow.instructions(), frame, allocas);
        IrValue.Const thunk = new IrValue.Const(IrType.PTR, "@" + resumeThunkName(function));
        slow.instructions().add(call(null, "jnative_async_awaitCooperative", IrType.VOID,
                List.of(a.future(), frame,
                        new IrValue.Const(IrType.I32, String.valueOf(a.resumeState())),
                        thunk)));
        IrValue.Temp rf = temp(IrType.PTR);
        slow.instructions().add(call(rf, "jnative_async_result_future", IrType.PTR, List.of(frame)));
        slow.instructions().add(new IrInstruction.Return(rf));

        containingBlock.setTerminated(true);
    }

    private void emitLoadFutureResultToSlot(
            List<IrInstruction> out, IrValue.Local slot, IrValue future, IrType rt) {
        if (slot == null) {
            return;
        }
        IrValue.Temp loaded = temp(rt == null ? IrType.PTR : rt);
        if (rt == IrType.PTR || rt == IrType.NATIVE_PTR || rt == null) {
            out.add(call(loaded, "jnative_future_getResult", IrType.PTR, List.of(future)));
        } else {
            IrValue.Temp boxed = temp(IrType.PTR);
            out.add(call(boxed, "jnative_future_getResult", IrType.PTR, List.of(future)));
            out.add(call(loaded, unboxHelper(rt), rt, List.of(boxed)));
        }
        out.add(new IrInstruction.Store(loaded, slot));
    }

    private void emitAwaitOutcomeToSlot(
            List<IrInstruction> out, IrValue.Local slot, IrValue frame, int resumeState) {
        if (frame == null) {
            return;
        }
        IrValue.Temp err = temp(IrType.PTR);
        out.add(call(err, "jnative_async_load_await_error", IrType.PTR, List.of(frame)));
        out.add(call(null, "jnative_async_rethrow_await_error", IrType.VOID, List.of(frame, err)));
        if (slot != null) {
            IrType rt = resumeTypes.getOrDefault(resumeState, IrType.PTR);
            IrValue.Temp loaded = temp(rt);
            if (rt == IrType.PTR || rt == IrType.NATIVE_PTR) {
                out.add(call(loaded, "jnative_async_load_await_result", IrType.PTR, List.of(frame)));
            } else {
                IrValue.Temp boxed = temp(IrType.PTR);
                out.add(call(boxed, "jnative_async_load_await_result", IrType.PTR, List.of(frame)));
                out.add(call(loaded, unboxHelper(rt), rt, List.of(boxed)));
            }
            out.add(new IrInstruction.Store(loaded, slot));
        }
    }

    private void insertStateDispatch(
            IrFunction function,
            IrValue frame,
            Map<Integer, String> resumeLabels,
            List<IrValue.Local> allocas
    ) {
        IrBasicBlock entry = function.blocks().get(0);
        List<IrInstruction> insts = entry.instructions();
        int splitAt = -1;
        for (int i = 0; i < insts.size(); i++) {
            if (insts.get(i) instanceof IrInstruction.Call c
                    && "jnative_async_select_nonzero".equals(c.function())) {
                splitAt = i + 1;
            }
        }
        if (splitAt < 0) {
            return;
        }

        List<IrInstruction> head = new ArrayList<>(insts.subList(0, splitAt));
        List<IrInstruction> tail = new ArrayList<>(insts.subList(splitAt, insts.size()));

        // Do not spill here — on resume allocas are not yet restored; spill only at await.

        IrBasicBlock body0 = function.addBlock("async.state0." + blockId++);
        IrValue.Temp state = temp(IrType.I32);
        head.add(call(state, "jnative_async_get_state", IrType.I32, List.of(frame)));

        if (resumeLabels.isEmpty()) {
            head.add(new IrInstruction.Branch(body0.name()));
            entry.instructions().clear();
            entry.instructions().addAll(head);
            entry.recalcTerminated();
            body0.instructions().add(new IrInstruction.JumpLabel(body0.name()));
            body0.instructions().addAll(tail);
            body0.recalcTerminated();
            return;
        }

        IrBasicBlock test = function.addBlock("async.dispatch." + blockId++);
        IrValue.Temp is0 = temp(IrType.I1);
        head.add(new IrInstruction.Compare(is0, IrInstruction.CmpOp.EQ,
                state, new IrValue.Const(IrType.I32, "0")));
        head.add(new IrInstruction.CondBranch(is0, body0.name(), test.name()));

        entry.instructions().clear();
        entry.instructions().addAll(head);
        entry.recalcTerminated();

        body0.instructions().add(new IrInstruction.JumpLabel(body0.name()));
        body0.instructions().addAll(tail);
        body0.recalcTerminated();

        List<Map.Entry<Integer, String>> entries = new ArrayList<>(resumeLabels.entrySet());
        IrBasicBlock cursor = test;
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<Integer, String> e = entries.get(i);
            cursor.instructions().add(new IrInstruction.JumpLabel(cursor.name()));
            IrValue.Temp eq = temp(IrType.I1);
            cursor.instructions().add(new IrInstruction.Compare(eq, IrInstruction.CmpOp.EQ,
                    state, new IrValue.Const(IrType.I32, String.valueOf(e.getKey()))));
            if (i + 1 < entries.size()) {
                IrBasicBlock next = function.addBlock("async.dispatch." + blockId++);
                cursor.instructions().add(new IrInstruction.CondBranch(eq, e.getValue(), next.name()));
                cursor.recalcTerminated();
                cursor = next;
            } else {
                cursor.instructions().add(new IrInstruction.CondBranch(eq, e.getValue(), body0.name()));
                cursor.recalcTerminated();
            }
        }
    }

    private static String resumeThunkName(IrFunction function) {
        // Avoid '$' — unquoted LLVM global names reject it.
        return function.name() + "__async_resume";
    }

    private void fillResumeThunk(IrFunction function) {
        String thunkName = resumeThunkName(function);
        IrFunction thunk = null;
        for (IrFunction f : module.functions()) {
            if (f.name().equals(thunkName)) {
                thunk = f;
                break;
            }
        }
        if (thunk == null) {
            ensureResumeThunkStub(function);
            for (IrFunction f : module.functions()) {
                if (f.name().equals(thunkName)) {
                    thunk = f;
                    break;
                }
            }
        }
        if (thunk == null) {
            return;
        }
        int savedTemp = tempId;
        tempId = 20_000;

        IrBasicBlock entry = thunk.blocks().get(0);
        entry.instructions().clear();
        entry.setTerminated(false);
        entry.instructions().add(new IrInstruction.JumpLabel("entry"));
        IrValue.Param frameParam = thunk.params().get(0);
        entry.instructions().add(call(null, "jnative_async_prepare_resume", IrType.VOID, List.of(frameParam)));
        entry.instructions().add(call(null, "jnative_async_set_resume", IrType.VOID, List.of(frameParam)));

        List<IrValue> args = new ArrayList<>();
        int slot = 0;
        for (IrValue.Param p : function.params()) {
            if (p.type() == IrType.PTR || p.type() == IrType.NATIVE_PTR) {
                IrValue.Temp v = temp(IrType.PTR);
                entry.instructions().add(call(v, "jnative_async_load_ptr_slot", IrType.PTR,
                        List.of(frameParam, new IrValue.Const(IrType.I32, String.valueOf(slot)))));
                args.add(v);
            } else {
                IrValue.Temp raw = temp(IrType.I64);
                entry.instructions().add(call(raw, "jnative_async_load_slot", IrType.I64,
                        List.of(frameParam, new IrValue.Const(IrType.I32, String.valueOf(slot)))));
                IrValue.Temp v = temp(p.type());
                entry.instructions().add(call(v, truncHelper(p.type()), p.type(), List.of(raw)));
                args.add(v);
            }
            slot++;
        }
        if (function.returnType() == IrType.VOID) {
            entry.instructions().add(new IrInstruction.Call(null, function.name(), IrType.VOID, args));
        } else {
            IrValue.Temp ignored = temp(function.returnType());
            entry.instructions().add(new IrInstruction.Call(
                    ignored, function.name(), function.returnType(), args));
        }
        entry.instructions().add(new IrInstruction.Return());
        entry.recalcTerminated();
        tempId = savedTemp;
    }

    private static List<IrValue.Local> collectAllocas(IrFunction function) {
        List<IrValue.Local> list = new ArrayList<>();
        for (IrBasicBlock b : function.blocks()) {
            for (IrInstruction inst : b.instructions()) {
                if (inst instanceof IrInstruction.Alloca a && !list.contains(a.dest())) {
                    list.add(a.dest());
                }
            }
        }
        return list;
    }

    private void emitSave(List<IrInstruction> out, IrValue frame, List<IrValue.Local> allocas) {
        int refSlot = 0;
        int scalarSlot = 0;
        for (IrValue.Local local : allocas) {
            IrValue.Temp loaded = temp(local.type());
            out.add(new IrInstruction.Load(loaded, local));
            if (local.type() == IrType.PTR || local.type() == IrType.NATIVE_PTR) {
                if (refSlot < 16) {
                    out.add(call(null, "jnative_async_save_ptr_slot", IrType.VOID,
                            List.of(frame, new IrValue.Const(IrType.I32, String.valueOf(refSlot)), loaded)));
                    refSlot++;
                }
            } else if (scalarSlot < 16) {
                IrValue.Temp wide = temp(IrType.I64);
                out.add(call(wide, zextHelper(local.type()), IrType.I64, List.of(loaded)));
                out.add(call(null, "jnative_async_save_slot", IrType.VOID,
                        List.of(frame, new IrValue.Const(IrType.I32, String.valueOf(scalarSlot)), wide)));
                scalarSlot++;
            }
        }
    }

    private void emitRestore(List<IrInstruction> out, IrValue frame, List<IrValue.Local> allocas) {
        if (frame == null) {
            return;
        }
        int refSlot = 0;
        int scalarSlot = 0;
        for (IrValue.Local local : allocas) {
            if (local.type() == IrType.PTR || local.type() == IrType.NATIVE_PTR) {
                if (refSlot < 16) {
                    IrValue.Temp v = temp(IrType.PTR);
                    out.add(call(v, "jnative_async_load_ptr_slot", IrType.PTR,
                            List.of(frame, new IrValue.Const(IrType.I32, String.valueOf(refSlot)))));
                    out.add(new IrInstruction.Store(v, local));
                    refSlot++;
                }
            } else if (scalarSlot < 16) {
                IrValue.Temp raw = temp(IrType.I64);
                out.add(call(raw, "jnative_async_load_slot", IrType.I64,
                        List.of(frame, new IrValue.Const(IrType.I32, String.valueOf(scalarSlot)))));
                IrValue.Temp v = temp(local.type());
                out.add(call(v, truncHelper(local.type()), local.type(), List.of(raw)));
                out.add(new IrInstruction.Store(v, local));
                scalarSlot++;
            }
        }
    }

    private static String zextHelper(IrType rt) {
        return switch (rt) {
            case I32 -> "jnative_async_zext_i32_to_i64";
            case I64 -> "jnative_async_zext_i64_to_i64";
            case I1 -> "jnative_async_zext_i1_to_i64";
            case I16 -> "jnative_async_zext_i16_to_i64";
            case I8 -> "jnative_async_zext_i8_to_i64";
            default -> "jnative_async_zext_i64_to_i64";
        };
    }

    private static String unboxHelper(IrType rt) {
        return switch (rt) {
            case I32 -> "jnative_async_unbox_i32";
            case I64 -> "jnative_async_unbox_i64";
            case I1 -> "jnative_async_unbox_i1";
            default -> "jnative_async_unbox_i32";
        };
    }

    private static String truncHelper(IrType rt) {
        return switch (rt) {
            case I32 -> "jnative_async_trunc_i32";
            case I64 -> "jnative_async_trunc_i64";
            case I1 -> "jnative_async_trunc_i1";
            case I16 -> "jnative_async_trunc_i16";
            case I8 -> "jnative_async_trunc_i8";
            default -> "jnative_async_trunc_i64";
        };
    }

    private IrValue.Temp temp(IrType type) {
        return new IrValue.Temp(tempId++, type);
    }

    private IrInstruction.Call call(IrValue.Temp dest, String fn, IrType ret, List<IrValue> args) {
        return new IrInstruction.Call(dest, fn, ret, args);
    }
}
