package io.jnative.ir;

import io.jnative.compiler.CompilerOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowers high-level async IR ({@link IrInstruction.AsyncBegin}, {@link IrInstruction.Await}, …)
 * into cooperative runtime calls before LLVM emission.
 *
 * <p>State-machine design (JNative 0.23):
 * <ol>
 *   <li>{@code AsyncBegin} → {@code jnative_async_begin(methodId)} allocates an {@code AsyncFrame}
 *       GC object holding resume state and live locals across await.</li>
 *   <li>{@code Await} → check {@code jnative_future_isComplete(future)}; fast path loads result via
 *       {@code jnative_future_getResult}; slow path calls
 *       {@code jnative_async_awaitCooperative(frame, resumeState, future)} and returns the pending
 *       future (cooperative yield — never blocks the event loop).</li>
 *   <li>Continuation re-enters the async method; {@code jnative_async_get_state(frame)} dispatches
 *       to the {@code AsyncResume} label (resume block emitted by {@link IrGenerator}).</li>
 *   <li>{@code AsyncComplete} → {@code jnative_future_completed(value)} + return.</li>
 * </ol>
 */
public final class AsyncLoweringPass {

    private int tempId;
    private int blockId;

    public void run(IrModule module, CompilerOptions options) {
        for (IrFunction function : module.functions()) {
            if (containsAsyncOps(function)) {
                function.setAsync(true);
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
        tempId = 0;
        blockId = 0;
        IrValue frame = null;

        for (IrBasicBlock block : function.blocks()) {
            List<IrInstruction> out = new ArrayList<>();
            for (IrInstruction inst : block.instructions()) {
                switch (inst) {
                    case IrInstruction.AsyncBegin b -> {
                        IrValue.Temp frameTemp = temp(IrType.PTR);
                        out.add(newCall(frameTemp, "jnative_async_begin", IrType.PTR, List.of()));
                        frame = frameTemp;
                    }
                    case IrInstruction.Await a -> {
                        if (frame == null) {
                            throw new IllegalStateException("Await before AsyncBegin in " + function.name());
                        }
                        expandAwait(function, block, out, a, frame);
                    }
                    case IrInstruction.AsyncResume ignored -> {
                        // resume label marker — no code
                    }
                    case IrInstruction.AsyncComplete c -> {
                        IrValue.Temp future = temp(IrType.PTR);
                        out.add(newCall(future, "jnative_future_completed", IrType.PTR, List.of(c.value())));
                        out.add(new IrInstruction.Return(future));
                    }
                    case IrInstruction.AsyncFail f -> {
                        out.add(newCall(null, "jnative_async_fail", IrType.VOID, List.of(f.exception())));
                        out.add(new IrInstruction.Return(new IrValue.Const(IrType.PTR, "null")));
                    }
                    case IrInstruction.AsyncSuspend s -> {
                        out.add(newCall(null, "jnative_async_suspend", IrType.VOID,
                                List.of(s.frame(),
                                        new IrValue.Const(IrType.I32, String.valueOf(s.resumeState())),
                                        s.future())));
                    }
                    default -> out.add(inst);
                }
            }
            block.instructions().clear();
            block.instructions().addAll(out);
            block.recalcTerminated();
        }
    }

    private void expandAwait(
            IrFunction function,
            IrBasicBlock containingBlock,
            List<IrInstruction> out,
            IrInstruction.Await a,
            IrValue frame
    ) {
        IrBasicBlock fast = function.addBlock("await." + a.resumeState() + ".fast." + blockId++);
        IrBasicBlock slow = function.addBlock("await." + a.resumeState() + ".slow." + blockId++);
        String resumeLabel = a.resumeLabel();

        IrValue.Temp done = temp(IrType.I1);
        out.add(newCall(done, "jnative_future_isComplete", IrType.I1, List.of(a.future())));
        out.add(new IrInstruction.CondBranch(done, fast.name(), slow.name()));

        fast.instructions().add(new IrInstruction.JumpLabel(fast.name()));
        if (a.dest() != null) {
            fast.instructions().add(newCall(a.dest(), "jnative_future_getResult", a.resultType(), List.of(a.future())));
        }
        fast.instructions().add(new IrInstruction.Branch(resumeLabel));

        slow.instructions().add(new IrInstruction.JumpLabel(slow.name()));
        slow.instructions().add(newCall(null, "jnative_async_awaitCooperative", IrType.VOID,
                List.of(a.future(), frame,
                        new IrValue.Const(IrType.I32, String.valueOf(a.resumeState())))));
        slow.instructions().add(new IrInstruction.Return(a.future()));

        containingBlock.setTerminated(true);
    }

    private IrValue.Temp temp(IrType type) {
        return new IrValue.Temp(tempId++, type);
    }

    private IrInstruction.Call newCall(IrValue.Temp dest, String fn, IrType ret, List<IrValue> args) {
        return new IrInstruction.Call(dest, fn, ret, args);
    }
}
