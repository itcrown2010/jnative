package io.jnative.opt;

import io.jnative.ir.IrBasicBlock;
import io.jnative.ir.IrFunction;
import io.jnative.ir.IrInstruction;
import io.jnative.ir.IrValue;
import io.jnative.opt.EscapeState.EscapeKind;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Intraprocedural escape analysis for NewObject temps. */
public final class EscapeAnalysisPass implements OptimizationPass {
    @Override
    public String name() {
        return "escape-analysis";
    }

    @Override
    public void run(OptimizationContext ctx) {
        for (IrFunction function : ctx.module().functions()) {
            analyzeFunction(ctx, function);
        }
    }

    private void analyzeFunction(OptimizationContext ctx, IrFunction function) {
        var states = ctx.escapeStates(function);
        states.clear();
        Map<IrValue.Temp, IrValue> callableEnvs = new HashMap<>();
        Map<IrValue.Temp, IrValue.Temp> tempRoot = new HashMap<>();
        Map<IrValue.Local, IrValue.Temp> localRoot = new HashMap<>();

        for (IrBasicBlock block : function.blocks()) {
            for (IrInstruction inst : block.instructions()) {
                if (inst instanceof IrInstruction.NewObject n) {
                    states.put(n.dest(), EscapeState.noEscape(n.className()));
                    tempRoot.put(n.dest(), n.dest());
                }
                if (inst instanceof IrInstruction.MakeCallable m && m.hasEnv()) {
                    callableEnvs.put(m.dest(), m.envOrNull());
                }
            }
        }

        // Propagate allocation identity and callable→env links through local aliases.
        Map<IrValue.Local, IrValue> localCallableEnv = new HashMap<>();
        for (int iter = 0; iter < 64; iter++) {
            boolean aliasChanged = false;
            for (IrBasicBlock block : function.blocks()) {
                for (IrInstruction inst : block.instructions()) {
                    if (inst instanceof IrInstruction.Store s
                            && s.dest() instanceof IrValue.Local local
                            && s.value() instanceof IrValue.Temp t) {
                        IrValue.Temp root = tempRoot.get(t);
                        if (root != null && localRoot.get(local) != root) {
                            localRoot.put(local, root);
                            aliasChanged = true;
                        }
                        IrValue env = callableEnvs.get(t);
                        if (env != null && localCallableEnv.get(local) != env) {
                            localCallableEnv.put(local, env);
                            aliasChanged = true;
                        }
                    }
                    if (inst instanceof IrInstruction.Load l
                            && l.source() instanceof IrValue.Local local) {
                        IrValue.Temp root = localRoot.get(local);
                        if (root != null && tempRoot.get(l.dest()) != root) {
                            tempRoot.put(l.dest(), root);
                            aliasChanged = true;
                        }
                        IrValue env = localCallableEnv.get(local);
                        if (env != null && callableEnvs.get(l.dest()) != env) {
                            callableEnvs.put(l.dest(), env);
                            aliasChanged = true;
                        }
                    }
                }
            }
            if (!aliasChanged) {
                break;
            }
        }

        for (int iter = 0; iter < 64; iter++) {
            boolean changed = false;
            for (IrBasicBlock block : function.blocks()) {
                for (IrInstruction inst : block.instructions()) {
                    changed |= process(states, callableEnvs, tempRoot, inst);
                }
            }
            if (!changed) {
                break;
            }
        }

        if (ctx.emitEscapeAnalysis()) {
            for (var e : states.entrySet()) {
                ctx.report().note(function.name() + " " + e.getKey().name()
                        + " -> " + e.getValue().kind()
                        + (e.getValue().exactClass() != null ? " (" + e.getValue().exactClass() + ")" : ""));
            }
        }
    }

    private boolean process(
            Map<IrValue.Temp, EscapeState> states,
            Map<IrValue.Temp, IrValue> callableEnvs,
            Map<IrValue.Temp, IrValue.Temp> tempRoot,
            IrInstruction inst
    ) {
        return switch (inst) {
            case IrInstruction.Return r -> escalate(states, callableEnvs, tempRoot, r.value(), EscapeKind.RETURN_ESCAPE);
            case IrInstruction.StoreField s -> escapeViaStoreInto(states, callableEnvs, tempRoot, s.receiver(), s.value());
            case IrInstruction.StoreStatic s -> escalate(states, callableEnvs, tempRoot, s.value(), EscapeKind.GLOBAL_ESCAPE);
            case IrInstruction.StoreElement s -> escapeViaStoreInto(states, callableEnvs, tempRoot, s.array(), s.value())
                    | escalate(states, callableEnvs, tempRoot, s.array(), EscapeKind.ARG_ESCAPE);
            case IrInstruction.Call c -> escapeViaCall(states, callableEnvs, tempRoot, c.args());
            case IrInstruction.VirtualCall v -> escalate(states, callableEnvs, tempRoot, v.receiver(), EscapeKind.ARG_ESCAPE)
                    | escapeViaCall(states, callableEnvs, tempRoot, v.args());
            case IrInstruction.NativeCall n -> escapeViaCall(states, callableEnvs, tempRoot, n.args())
                    | markNative(states, callableEnvs, tempRoot, n.args());
            case IrInstruction.ConstructorCall cc -> escapeViaConstructor(states, callableEnvs, tempRoot, cc);
            case IrInstruction.MakeCallable ignored -> false;
            case IrInstruction.CreateNativeCallback c ->
                    escalate(states, callableEnvs, tempRoot, c.callable(), EscapeKind.NATIVE_ESCAPE);
            case IrInstruction.InvokeCallable i -> escalate(states, callableEnvs, tempRoot, i.callable(), EscapeKind.ARG_ESCAPE)
                    | escapeViaCall(states, callableEnvs, tempRoot, i.args());
            case IrInstruction.Throw t -> escalate(states, callableEnvs, tempRoot, t.exception(), EscapeKind.ARG_ESCAPE);
            default -> false;
        };
    }

    /**
     * Storing into a receiver does not escape the receiver.
     * The stored value escapes at least as much as the receiver (GLOBAL if receiver unknown).
     */
    private boolean escapeViaStoreInto(
            Map<IrValue.Temp, EscapeState> states,
            Map<IrValue.Temp, IrValue> callableEnvs,
            Map<IrValue.Temp, IrValue.Temp> tempRoot,
            IrValue receiver,
            IrValue value
    ) {
        EscapeKind recvKind = kindOf(states, tempRoot, receiver);
        EscapeKind forValue = recvKind != null ? recvKind : EscapeKind.GLOBAL_ESCAPE;
        return escalate(states, callableEnvs, tempRoot, value, forValue);
    }

    private EscapeKind kindOf(
            Map<IrValue.Temp, EscapeState> states,
            Map<IrValue.Temp, IrValue.Temp> tempRoot,
            IrValue value
    ) {
        IrValue.Temp root = resolveRoot(tempRoot, value);
        if (root == null) {
            return null;
        }
        EscapeState st = states.get(root);
        return st == null ? null : st.kind();
    }

    private IrValue.Temp resolveRoot(Map<IrValue.Temp, IrValue.Temp> tempRoot, IrValue value) {
        if (!(value instanceof IrValue.Temp t)) {
            return null;
        }
        return tempRoot.get(t);
    }

    private boolean escapeViaConstructor(
            Map<IrValue.Temp, EscapeState> states,
            Map<IrValue.Temp, IrValue> callableEnvs,
            Map<IrValue.Temp, IrValue.Temp> tempRoot,
            IrInstruction.ConstructorCall cc
    ) {
        boolean changed = false;
        for (int i = 0; i < cc.args().size(); i++) {
            if (i == 0) {
                continue; // receiver initialization is not an escape
            }
            changed |= escalate(states, callableEnvs, tempRoot, cc.args().get(i), EscapeKind.ARG_ESCAPE);
        }
        return changed;
    }

    private boolean escapeViaCall(
            Map<IrValue.Temp, EscapeState> states,
            Map<IrValue.Temp, IrValue> callableEnvs,
            Map<IrValue.Temp, IrValue.Temp> tempRoot,
            java.util.List<IrValue> args
    ) {
        boolean changed = false;
        for (IrValue arg : args) {
            changed |= escalate(states, callableEnvs, tempRoot, arg, EscapeKind.ARG_ESCAPE);
        }
        return changed;
    }

    private boolean markNative(
            Map<IrValue.Temp, EscapeState> states,
            Map<IrValue.Temp, IrValue> callableEnvs,
            Map<IrValue.Temp, IrValue.Temp> tempRoot,
            java.util.List<IrValue> args
    ) {
        boolean changed = false;
        for (IrValue arg : args) {
            changed |= escalate(states, callableEnvs, tempRoot, arg, EscapeKind.NATIVE_ESCAPE);
        }
        return changed;
    }

    private boolean escalate(
            Map<IrValue.Temp, EscapeState> states,
            Map<IrValue.Temp, IrValue> callableEnvs,
            Map<IrValue.Temp, IrValue.Temp> tempRoot,
            IrValue value,
            EscapeKind kind
    ) {
        if (value == null) {
            return false;
        }
        boolean changed = false;
        Set<IrValue.Temp> roots = new HashSet<>();
        if (value instanceof IrValue.Temp t) {
            IrValue.Temp root = tempRoot.get(t);
            if (root != null) {
                roots.add(root);
            } else if (states.containsKey(t)) {
                roots.add(t);
            }
            // Callable dest may not be a NewObject; still propagate to env.
            IrValue env = callableEnvs.get(t);
            if (env != null) {
                changed |= escalate(states, callableEnvs, tempRoot, env, kind);
            }
        }
        for (IrValue.Temp root : roots) {
            EscapeState old = states.get(root);
            if (old == null) {
                continue;
            }
            EscapeState next = old.escalate(kind);
            if (!next.equals(old)) {
                states.put(root, next);
                changed = true;
            }
        }
        return changed;
    }
}
