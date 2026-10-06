package io.jnative.opt;

import io.jnative.ir.IrFunction;
import io.jnative.ir.IrInstruction;
import io.jnative.ir.IrModule;
import io.jnative.ir.IrValue;
import io.jnative.opt.cfg.ControlFlowGraph;
import io.jnative.opt.cfg.DefUse;
import io.jnative.opt.cfg.LoopInfo;
import io.jnative.semantic.ClassSymbol;
import io.jnative.semantic.FunctionEffects;
import io.jnative.semantic.MethodSymbol;
import io.jnative.semantic.TypedCompilationUnit;
import io.jnative.vtable.VTable;
import io.jnative.vtable.VTableEntry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Per-compilation optimization state and cached analyses. */
public final class OptimizationContext {
    private final IrModule module;
    private final TypedCompilationUnit typed;
    private final int optLevel;
    private final OptimizationReport report;
    private final boolean emitEscapeAnalysis;
    private final Map<String, FunctionEffects> effectsByMangled = new HashMap<>();
    private final Map<IrFunction, ControlFlowGraph> cfgCache = new HashMap<>();
    private final Map<IrFunction, DefUse> defUseCache = new HashMap<>();
    private final Map<IrFunction, LoopInfo> loopCache = new HashMap<>();
    private final Map<IrFunction, Map<IrValue.Temp, EscapeState>> escapeStates = new HashMap<>();
    private int nextTempId;
    private int inlineBudget = 64;

    public OptimizationContext(
            IrModule module,
            TypedCompilationUnit typed,
            int optLevel,
            OptimizationReport report,
            boolean emitEscapeAnalysis
    ) {
        this.module = module;
        this.typed = typed;
        this.optLevel = optLevel;
        this.report = report;
        this.emitEscapeAnalysis = emitEscapeAnalysis;
        this.nextTempId = IrUtil.maxTempId(module) + 1;
        buildEffectsMap();
    }

    public IrModule module() {
        return module;
    }

    public TypedCompilationUnit typed() {
        return typed;
    }

    public int optLevel() {
        return optLevel;
    }

    public OptimizationReport report() {
        return report;
    }

    public boolean emitEscapeAnalysis() {
        return emitEscapeAnalysis;
    }

    public IrValue.Temp freshTemp(io.jnative.ir.IrType type) {
        return new IrValue.Temp(nextTempId++, type);
    }

    public int inlineBudget() {
        return inlineBudget;
    }

    public void consumeInline() {
        inlineBudget--;
    }

    public boolean canInline() {
        return inlineBudget > 0;
    }

    public ControlFlowGraph getCfg(IrFunction function) {
        return cfgCache.computeIfAbsent(function, ControlFlowGraph::new);
    }

    public DefUse getDefUse(IrFunction function) {
        return defUseCache.computeIfAbsent(function, DefUse::new);
    }

    public LoopInfo getLoopInfo(IrFunction function) {
        return loopCache.computeIfAbsent(function, f -> new LoopInfo(getCfg(f)));
    }

    public void invalidateFunction(IrFunction function) {
        cfgCache.remove(function);
        defUseCache.remove(function);
        loopCache.remove(function);
        // Escape states are owned by EscapeAnalysisPass (cleared/rebuilt there).
        // Do not drop them here — later passes (stack alloc, scalar replace) depend on them.
    }

    public void invalidateAll() {
        invalidateAnalyses();
    }

    public void invalidateAnalyses() {
        cfgCache.clear();
        defUseCache.clear();
        loopCache.clear();
    }

    public Map<IrValue.Temp, EscapeState> escapeStates(IrFunction function) {
        return escapeStates.computeIfAbsent(function, f -> new HashMap<>());
    }

    public String exactClass(IrFunction function, IrValue.Temp temp) {
        EscapeState st = escapeStates(function).get(temp);
        return st != null ? st.exactClass() : null;
    }

    public FunctionEffects effects(String mangledName) {
        return effectsByMangled.getOrDefault(mangledName, FunctionEffects.unknown());
    }

    public boolean isPureCall(String mangledName) {
        return effects(mangledName).isPure();
    }

    public boolean isPureVirtual(IrFunction function, IrInstruction.VirtualCall call) {
        String cls = null;
        if (call.receiver() instanceof IrValue.Temp t) {
            cls = exactClass(function, t);
            if (cls == null) {
                cls = IrUtil.traceToNewObjectClass(function, t);
            }
        }
        if (cls == null && call.debugName() != null && call.debugName().contains(".")) {
            cls = call.debugName().substring(0, call.debugName().indexOf('.'));
        }
        if (cls == null) {
            return false;
        }
        VTable vt = module.vtables().get(cls);
        if (vt == null || call.slot() >= vt.size()) {
            return false;
        }
        return vt.entry(call.slot())
                .map(e -> effects(e.mangledImplementation()).isPure())
                .orElse(false);
    }

    public boolean isPureNative(String symbol) {
        return false;
    }

    private void buildEffectsMap() {
        for (var cls : typed.classes().values()) {
            for (MethodSymbol ms : cls.declaredMethods()) {
                effectsByMangled.put(ms.mangledName(), ms.effectiveEffects());
            }
            if (cls.superClass().isPresent()) {
                collectInheritedEffects(cls.superClass().get());
            }
        }
    }

    private void collectInheritedEffects(ClassSymbol cls) {
        for (MethodSymbol ms : cls.declaredMethods()) {
            effectsByMangled.putIfAbsent(ms.mangledName(), ms.effectiveEffects());
        }
        cls.superClass().ifPresent(this::collectInheritedEffects);
    }

    /** Count distinct implementations for a vtable slot and method name (CHA). */
    public int countDistinctImplementations(int slot, String methodName) {
        Set<String> impls = new HashSet<>();
        for (VTable vt : module.vtables().values()) {
            if (slot >= vt.size()) {
                continue;
            }
            VTableEntry entry = vt.entries().get(slot);
            if (methodName == null || entry.implementation().name().equals(methodName)) {
                impls.add(entry.mangledImplementation());
            }
        }
        return impls.size();
    }

    public String singleImplementationForMethod(int slot, String methodName) {
        String found = null;
        for (VTable vt : module.vtables().values()) {
            if (slot >= vt.size()) {
                continue;
            }
            VTableEntry entry = vt.entries().get(slot);
            if (methodName != null && !entry.implementation().name().equals(methodName)) {
                continue;
            }
            String impl = entry.mangledImplementation();
            if (found == null) {
                found = impl;
            } else if (!found.equals(impl)) {
                return null;
            }
        }
        return found;
    }

    /** Closed-world: unique ITable implementation for interface id + slot, or null. */
    public String singleInterfaceImplementation(int interfaceId, int slot) {
        String found = null;
        for (var itable : module.itables().values()) {
            if (itable.interfaceSymbol().interfaceId() != interfaceId) {
                continue;
            }
            if (slot < 0 || slot >= itable.size()) {
                continue;
            }
            String impl = itable.entries().get(slot).mangledImplementation();
            if (found == null) {
                found = impl;
            } else if (!found.equals(impl)) {
                return null;
            }
        }
        return found;
    }

    public Set<String> protectedFunctionNames() {
        Set<String> keep = new HashSet<>();
        keep.add("jn_main");
        keep.add("jnative_clinit");
        for (IrFunction f : module.functions()) {
            if (f.isMain()) {
                keep.add(f.name());
            }
            if (f.name().endsWith("_main")) {
                keep.add(f.name());
            }
            if (f.name().startsWith("jnative_cb_trampoline_")) {
                keep.add(f.name());
            }
        }
        for (var init : module.staticInits()) {
            if (init.initFunction() != null) {
                keep.add(init.initFunction());
            }
        }
        return keep;
    }
}
