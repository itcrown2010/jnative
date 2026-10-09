package io.jnative.backend.wasm;

import io.jnative.ir.IrBasicBlock;
import io.jnative.ir.IrFunction;
import io.jnative.ir.IrInstruction;
import io.jnative.ir.IrModule;
import io.jnative.ir.IrType;
import io.jnative.ir.IrValue;
import io.jnative.layout.ClassLayout;
import io.jnative.layout.FieldLayout;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Lowers reachable JNative IR functions to WAT for wasm32-browser.
 * Uses a basic-block dispatcher (br_table) so arbitrary CFG / loops lower correctly.
 * Unsupported IR fails loudly — never silently returns a default.
 */
public final class WebUiIrToWat {

    private static final Set<String> SKIP_PREFIXES = Set.of(
            "_JN_Object_",
            "_JN_String_",
            "_JN_StringBuilder_",
            "_JN_System_",
            "_JN_Thread_",
            "_JN_Throwable_",
            "_JN_Exception_",
            "_JN_RuntimeException_",
            "_JN_Error_",
            "_JN_Class_",
            "_JN_Integer_",
            "_JN_Long_",
            "_JN_Boolean_",
            "_JN_Character_",
            "_JN_Float_",
            "_JN_Double_",
            "_JN_Math_",
            "_JN_Arrays_",
            "_JN_ArrayList_",
            "_JN_ArrayListIterator_",
            "_JN_HashMap_",
            "_JN_HashMapIterator_",
            "_JN_LinkedList_",
            "_JN_BrowserNative_",
            "_JN_WebUiNative_",
            "_JN_WebUI_",
            "_JN_Html_",
            "_JN_IntState_",
            "_JN_WebUiComponent_",
            "_JN_Mutex_",
            "_JN_Pool_",
            "_JN_PoolTask_",
            "_JN_ThreadPool_",
            "_JN_ThreadPoolExecutor_",
            "_JN_ThreadPoolWorker_",
            "_JN_Future_",
            "_JN_CompletableFuture_",
            "_JN_EventLoop_",
            "_JN_Scheduled_",
            "_JN_Timer_"
    );

    private final IrModule module;
    private final Map<String, Integer> stringOffsets;
    private final WasmMetaRegistry meta;
    private final Map<String, IrFunction> functionsByName = new HashMap<>();
    private final Set<String> emitSet = new LinkedHashSet<>();
    private final Set<String> exportSet = new LinkedHashSet<>();
    private final Set<String> staticGlobals = new LinkedHashSet<>();
    /** Class names seen in reachable NewObject / MakeCallable (limits vcall fan-out). */
    private final Set<String> liveClasses = new LinkedHashSet<>();
    /** Param identifiers for the function currently being lowered (slot rename). */
    private Set<String> currentParamNames = Set.of();
    /** Alloca slot identifier → WAT type. */
    private Map<String, String> currentLocalWatTypes = Map.of();
    /** Temp id → WAT type. */
    private Map<Integer, String> currentTempWatTypes = Map.of();
    /** Static global name → WAT type. */
    private final Map<String, String> globalWatTypes = new HashMap<>();

    public WebUiIrToWat(IrModule module, Map<String, Integer> stringOffsets) {
        this.module = module;
        this.stringOffsets = stringOffsets;
        this.meta = new WasmMetaRegistry(module);
        for (IrFunction fn : module.functions()) {
            functionsByName.put(fn.name(), fn);
        }
        for (IrModule.StaticField sf : module.staticFields()) {
            staticGlobals.add(sf.globalName());
            globalWatTypes.put(sf.globalName(), watType(sf.type()));
        }
    }

    public WasmMetaRegistry meta() {
        return meta;
    }

    /** Indirect call signatures discovered during lowering: name → `(func ...)` body. */
    private final Map<String, String> indirectTypes = new LinkedHashMap<>();

    public Map<String, String> indirectTypes() {
        return indirectTypes;
    }

    private String internIndirectType(String funcBody) {
        // funcBody like "(func (param i32) (param i32))"
        for (Map.Entry<String, String> e : indirectTypes.entrySet()) {
            if (e.getValue().equals(funcBody)) {
                return e.getKey();
            }
        }
        String name = "jn_sig_" + indirectTypes.size();
        indirectTypes.put(name, funcBody);
        return name;
    }

    public String globalWatType(String globalName) {
        return globalWatTypes.getOrDefault(globalName, "i32");
    }

    public Set<String> emittedFunctions() {
        return emitSet;
    }

    public Set<String> exportedFunctions() {
        return exportSet;
    }

    public Set<String> staticGlobals() {
        return staticGlobals;
    }

    /**
     * Collect reachable functions from {@code jn_main} and all non-stdlib user {@code _JN_*} roots.
     */
    public void collect() {
        // Static field initializers live in jnative_clinit. Native LLVM always
        // invokes it before main; wasm32-browser must do the same or every
        // `static int X = N` / `static final int C = N` stays at the zero global.
        if (functionsByName.containsKey("jnative_clinit")) {
            collectReachable("jnative_clinit");
        }
        collectReachable("jn_main");
        for (IrFunction fn : module.functions()) {
            String name = fn.name();
            if (isUserApi(name)) {
                collectReachable(name);
                exportSet.add(name);
            }
        }
        if (functionsByName.containsKey("jn_main")) {
            exportSet.add("jn_main");
        }
        // Fixed point: NewObject discovers live classes → virtual/interface impls.
        boolean grew;
        do {
            grew = false;
            int before = emitSet.size();
            harvestLiveClasses();
            for (String name : new ArrayList<>(emitSet)) {
                IrFunction fn = functionsByName.get(name);
                if (fn == null) {
                    continue;
                }
                for (IrBasicBlock block : fn.blocks()) {
                    for (IrInstruction insn : block.instructions()) {
                        if (insn instanceof IrInstruction.VirtualCall vc) {
                            String mname = WasmMetaRegistry.methodNameFromDebug(vc.debugName());
                            for (String impl : liveVirtualImpls(vc.slot(), mname)) {
                                collectReachable(impl);
                            }
                        } else if (insn instanceof IrInstruction.InterfaceCall ic) {
                            for (String impl : liveInterfaceImpls(ic.interfaceId(), ic.slot())) {
                                collectReachable(impl);
                            }
                        }
                    }
                }
            }
            if (emitSet.size() > before) {
                grew = true;
            }
        } while (grew);
        for (String name : emitSet) {
            IrFunction fn = functionsByName.get(name);
            if (fn == null) {
                continue;
            }
            for (IrBasicBlock block : fn.blocks()) {
                for (IrInstruction insn : block.instructions()) {
                    if (insn instanceof IrInstruction.LoadStatic ls) {
                        staticGlobals.add(ls.globalName());
                    } else if (insn instanceof IrInstruction.StoreStatic ss) {
                        staticGlobals.add(ss.globalName());
                    } else if (insn instanceof IrInstruction.InvokeCallable icv) {
                        internIndirectType(watIndirectFuncBody(icv.returnType(), icv.args()));
                    } else if (insn instanceof IrInstruction.MakeCallable mc) {
                        meta.internFuncref(mc.codeSymbol());
                    }
                }
            }
        }
        ensureDomCallbackType();
    }

    private void harvestLiveClasses() {
        for (String name : emitSet) {
            IrFunction fn = functionsByName.get(name);
            if (fn == null) {
                continue;
            }
            for (IrBasicBlock block : fn.blocks()) {
                for (IrInstruction insn : block.instructions()) {
                    if (insn instanceof IrInstruction.NewObject no && no.className() != null) {
                        liveClasses.add(no.className());
                    } else if (insn instanceof IrInstruction.MakeCallable mc && mc.samClass() != null) {
                        liveClasses.add(mc.samClass());
                    }
                }
            }
        }
    }

    public void emitAll(StringBuilder out) {
        collect();
        for (String name : new ArrayList<>(emitSet)) {
            IrFunction fn = functionsByName.get(name);
            if (fn != null) {
                emitFunctionBody(fn, out);
            }
        }
        emitBuiltinStubs(out);
        for (String name : exportSet) {
            if (!emitSet.contains(name)) {
                continue;
            }
            out.append("  (export \"").append(sanitize(name)).append("\" (func $")
                    .append(sanitize(name)).append("))\n");
        }
    }

    private static boolean isUserApi(String name) {
        if (name == null || !name.startsWith("_JN_")) {
            return false;
        }
        for (String p : SKIP_PREFIXES) {
            if (name.startsWith(p)) {
                return false;
            }
        }
        return true;
    }

    private void collectReachable(String root) {
        Queue<String> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            String name = q.poll();
            if (name == null || !emitSet.add(name)) {
                continue;
            }
            IrFunction fn = functionsByName.get(name);
            if (fn == null) {
                continue;
            }
            for (IrBasicBlock block : fn.blocks()) {
                for (IrInstruction insn : block.instructions()) {
                    collectCallees(insn, q);
                }
            }
        }
    }

    private void collectCallees(IrInstruction insn, Queue<String> q) {
        switch (insn) {
            case IrInstruction.Call c -> {
                if (!isExternalBuiltin(c.function())) {
                    q.add(c.function());
                }
            }
            case IrInstruction.ConstructorCall cc -> q.add(cc.mangledName());
            case IrInstruction.VirtualCall vc -> {
                String fallback = resolveVirtualByName(vc);
                if (fallback != null) {
                    q.add(fallback);
                }
            }
            case IrInstruction.InterfaceCall ignored -> {
                // Resolved in collect() fixed-point once liveClasses is known.
            }
            case IrInstruction.MakeCallable mc -> {
                q.add(mc.codeSymbol());
                meta.internFuncref(mc.codeSymbol());
            }
            default -> {
            }
        }
    }

    private Set<String> liveVirtualImpls(int slot, String methodName) {
        Set<String> out = new LinkedHashSet<>();
        if (liveClasses.isEmpty()) {
            return out;
        }
        for (Map.Entry<String, Integer> e : meta.allMetaTypeIds().entrySet()) {
            String metaName = e.getKey();
            String cls = metaName.startsWith("meta.") ? metaName.substring(5) : metaName;
            if (!liveClasses.contains(cls)) {
                continue;
            }
            String impl = meta.resolveVirtualNamed(e.getValue(), slot, methodName);
            if (impl != null && !isHeavyObjectBuiltin(impl)) {
                out.add(impl);
            }
        }
        return out;
    }

    /** Object.toString / wait / etc. pull the full string runtime — exclude from wasm smoke paths. */
    private static boolean isHeavyObjectBuiltin(String mangled) {
        if (mangled == null || !mangled.startsWith("_JN_Object_")) {
            return false;
        }
        return !mangled.contains("_equals_") && !mangled.contains("_hashCode");
    }

    private Set<String> liveInterfaceImpls(int interfaceId, int slot) {
        Set<String> out = new LinkedHashSet<>();
        for (int tid : meta.implementorsOf(interfaceId)) {
            String metaName = null;
            for (Map.Entry<String, Integer> e : meta.allMetaTypeIds().entrySet()) {
                if (e.getValue() == tid) {
                    metaName = e.getKey();
                    break;
                }
            }
            if (metaName != null) {
                String cls = metaName.startsWith("meta.") ? metaName.substring(5) : metaName;
                boolean adapter = cls.startsWith("__jn.IFaceAdapter.");
                if (!adapter && (liveClasses.isEmpty() || !liveClasses.contains(cls))) {
                    continue;
                }
            }
            String impl = meta.resolveInterface(tid, interfaceId, slot);
            if (impl != null) {
                out.add(impl);
            }
        }
        return out;
    }

    private static boolean isExternalBuiltin(String fn) {
        // jnative_clinit is emitted from IR and must be reachable/collectable.
        if ("jnative_clinit".equals(fn)) {
            return false;
        }
        return fn.startsWith("jnative_")
                || fn.startsWith("_JN_BrowserNative_")
                || fn.startsWith("_JN_WebUiNative_")
                || fn.equals("_JN_Browser_console")
                || fn.equals("_JN_Browser_document")
                || fn.startsWith("_JN_Console_");
    }

    /** Name-prefix fallback when vtable metadata is missing for a monomorphic site. */
    private String resolveVirtualByName(IrInstruction.VirtualCall vc) {
        String debug = vc.debugName();
        if (debug == null) {
            return null;
        }
        int dot = debug.indexOf('.');
        if (dot <= 0) {
            return null;
        }
        String cls = debug.substring(0, dot);
        String method = debug.substring(dot + 1);
        String prefix = "_JN_" + cls + "_" + method;
        String found = null;
        for (String fn : functionsByName.keySet()) {
            if (fn.startsWith(prefix)) {
                if (found != null && !found.equals(fn)) {
                    return null; // ambiguous — must use typeId dispatch
                }
                found = fn;
            }
        }
        return found;
    }

    private void emitBuiltinStubs(StringBuilder out) {
        if (!emitSet.contains("jnative_exception_pending")) {
            out.append("  (func $jnative_exception_pending (result i32)\n");
            out.append("    global.get $jn_pending_exc\n");
            out.append("    i32.const 0\n");
            out.append("    i32.ne\n");
            out.append("  )\n");
        }
        emitTypeHelpers(out);
    }

    /** instanceof / interface checks driven by compile-time typeId tables. */
    private void emitTypeHelpers(StringBuilder out) {
        out.append("  (func $jn_super_of (param $tid i32) (result i32)\n");
        for (Map.Entry<Integer, Integer> e : meta.superMap().entrySet()) {
            out.append("    local.get $tid\n");
            out.append("    i32.const ").append(e.getKey()).append("\n");
            out.append("    i32.eq\n");
            out.append("    if\n");
            out.append("      i32.const ").append(e.getValue()).append("\n");
            out.append("      return\n");
            out.append("    end\n");
        }
        out.append("    i32.const 0\n");
        out.append("  )\n");

        out.append("  (func $jn_instanceof (param $obj i32) (param $target i32) (result i32)\n");
        out.append("    (local $tid i32)\n");
        out.append("    local.get $obj\n");
        out.append("    i32.eqz\n");
        out.append("    if (result i32)\n");
        out.append("      i32.const 0\n");
        out.append("    else\n");
        out.append("      local.get $obj\n");
        out.append("      i32.load\n");
        out.append("      local.set $tid\n");
        out.append("      (block $done (result i32)\n");
        out.append("        (loop $walk\n");
        out.append("          local.get $tid\n");
        out.append("          i32.eqz\n");
        out.append("          if\n");
        out.append("            i32.const 0\n");
        out.append("            br $done\n");
        out.append("          end\n");
        out.append("          local.get $tid\n");
        out.append("          local.get $target\n");
        out.append("          i32.eq\n");
        out.append("          if\n");
        out.append("            i32.const 1\n");
        out.append("            br $done\n");
        out.append("          end\n");
        out.append("          local.get $tid\n");
        out.append("          call $jn_super_of\n");
        out.append("          local.set $tid\n");
        out.append("          br $walk\n");
        out.append("        )\n");
        out.append("        i32.const 0\n");
        out.append("      )\n");
        out.append("    end\n");
        out.append("  )\n");

        out.append("  (func $jn_instanceof_iface (param $obj i32) (param $iid i32) (result i32)\n");
        out.append("    (local $tid i32)\n");
        out.append("    local.get $obj\n");
        out.append("    i32.eqz\n");
        out.append("    if (result i32)\n");
        out.append("      i32.const 0\n");
        out.append("    else\n");
        out.append("      local.get $obj\n");
        out.append("      i32.load\n");
        out.append("      local.set $tid\n");
        for (int iid : meta.allInterfaceIds()) {
            for (int tid : meta.implementorsOf(iid)) {
                out.append("      local.get $tid\n");
                out.append("      i32.const ").append(tid).append("\n");
                out.append("      i32.eq\n");
                out.append("      local.get $iid\n");
                out.append("      i32.const ").append(iid).append("\n");
                out.append("      i32.eq\n");
                out.append("      i32.and\n");
                out.append("      if\n");
                out.append("        i32.const 1\n");
                out.append("        return\n");
                out.append("      end\n");
            }
        }
        out.append("      i32.const 0\n");
        out.append("    end\n");
        out.append("  )\n");
    }

    private void emitFunctionBody(IrFunction fn, StringBuilder out) {
        String watName = sanitize(fn.name());
        out.append("  (func $").append(watName);
        for (IrValue.Param p : fn.params()) {
            out.append(" (param $").append(p.identifier()).append(" ").append(watType(p.type())).append(")");
        }
        boolean returns = fn.returnType() != IrType.VOID;
        if (returns) {
            out.append(" (result ").append(watType(fn.returnType())).append(")");
        }
        out.append("\n");

        List<IrBasicBlock> blocks = fn.blocks();
        Map<String, Integer> blockIndex = new HashMap<>();
        for (int i = 0; i < blocks.size(); i++) {
            blockIndex.put(blocks.get(i).name(), i);
        }

        Map<String, String> localTypes = new LinkedHashMap<>();
        Map<Integer, String> tempTypes = new HashMap<>();
        for (IrBasicBlock block : blocks) {
            for (IrInstruction insn : block.instructions()) {
                collectTypedLocals(insn, localTypes, tempTypes);
            }
        }
        currentLocalWatTypes = localTypes;
        currentTempWatTypes = tempTypes;

        out.append("    (local $pc i32)\n");
        out.append("    (local $__jn_tid i32)\n");
        out.append("    (local $__jn_asz i32)\n");
        if (returns) {
            out.append("    (local $ret ").append(watType(fn.returnType())).append(")\n");
        }
        currentParamNames = new HashSet<>();
        for (IrValue.Param p : fn.params()) {
            currentParamNames.add(p.identifier());
        }
        for (Map.Entry<String, String> e : localTypes.entrySet()) {
            out.append("    (local $").append(slotName(e.getKey())).append(" ")
                    .append(e.getValue()).append(")\n");
        }
        List<Integer> sortedTemps = new ArrayList<>(tempTypes.keySet());
        sortedTemps.sort(Integer::compareTo);
        for (Integer id : sortedTemps) {
            out.append("    (local $t").append(id).append(" ")
                    .append(tempTypes.get(id)).append(")\n");
        }

        out.append("    i32.const 0\n");
        out.append("    local.set $pc\n");
        out.append("    (block $exit\n");
        out.append("      (loop $dispatch\n");

        int n = blocks.size();
        // Nested blocks: br $bbi jumps to the *end* of block $bbi, so each
        // body's code must appear immediately after that block's closing ')'.
        for (int i = n - 1; i >= 0; i--) {
            out.append("        (block $bb").append(i).append("\n");
        }
        out.append("          local.get $pc\n");
        out.append("          br_table");
        for (int i = 0; i < n; i++) {
            out.append(" $bb").append(i);
        }
        out.append(" $exit\n");
        out.append("        )\n"); // close bb0 — br $bb0 lands after this

        for (int i = 0; i < n; i++) {
            IrBasicBlock block = blocks.get(i);
            boolean terminated = false;
            for (IrInstruction insn : block.instructions()) {
                if (insn instanceof IrInstruction.JumpLabel) {
                    continue;
                }
                if (emitInstruction(insn, out, blockIndex, returns)) {
                    terminated = true;
                }
            }
            if (!terminated) {
                if (i + 1 < n) {
                    out.append("          i32.const ").append(i + 1).append("\n");
                    out.append("          local.set $pc\n");
                    out.append("          br $dispatch\n");
                } else {
                    out.append("          br $exit\n");
                }
            }
            if (i + 1 < n) {
                // Close bb{i+1}; br $bb{i+1} lands after this, at bb{i+1}'s body.
                out.append("        )\n");
            }
        }

        out.append("      )\n"); // loop
        out.append("    )\n"); // exit
        if (returns) {
            out.append("    local.get $ret\n");
        }
        out.append("  )\n");
    }

    private void collectTypedLocals(
            IrInstruction insn,
            Map<String, String> localTypes,
            Map<Integer, String> tempTypes
    ) {
        switch (insn) {
            case IrInstruction.Alloca a ->
                    localTypes.put(a.dest().identifier(), watType(a.dest().type()));
            case IrInstruction.Load ld ->
                    tempTypes.put(ld.dest().id(), watType(ld.dest().type()));
            case IrInstruction.StringLiteral sl ->
                    tempTypes.put(sl.dest().id(), "i32");
            case IrInstruction.Call c -> {
                if (c.dest() != null) {
                    tempTypes.put(c.dest().id(), watType(c.dest().type()));
                }
            }
            case IrInstruction.Binary b ->
                    tempTypes.put(b.dest().id(), watType(b.dest().type()));
            case IrInstruction.Compare c ->
                    tempTypes.put(c.dest().id(), "i32");
            case IrInstruction.Cast c ->
                    tempTypes.put(c.dest().id(), watType(c.dest().type()));
            case IrInstruction.UnaryNot u ->
                    tempTypes.put(u.dest().id(), "i32");
            case IrInstruction.VirtualCall vc -> {
                if (vc.dest() != null) {
                    tempTypes.put(vc.dest().id(), watType(vc.dest().type()));
                }
            }
            case IrInstruction.InterfaceCall ic -> {
                if (ic.dest() != null) {
                    tempTypes.put(ic.dest().id(), watType(ic.dest().type()));
                }
            }
            case IrInstruction.NewObject no ->
                    tempTypes.put(no.dest().id(), "i32");
            case IrInstruction.NewArray na ->
                    tempTypes.put(na.dest().id(), "i32");
            case IrInstruction.LoadElement le ->
                    tempTypes.put(le.dest().id(), watType(le.dest().type()));
            case IrInstruction.ArrayLength al ->
                    tempTypes.put(al.dest().id(), "i32");
            case IrInstruction.LoadField lf ->
                    tempTypes.put(lf.dest().id(), watType(lf.dest().type()));
            case IrInstruction.LoadStatic ls ->
                    tempTypes.put(ls.dest().id(), watType(ls.dest().type()));
            case IrInstruction.InstanceOf io ->
                    tempTypes.put(io.dest().id(), "i32");
            case IrInstruction.InstanceOfInterface ioi ->
                    tempTypes.put(ioi.dest().id(), "i32");
            case IrInstruction.CheckedCast cc ->
                    tempTypes.put(cc.dest().id(), "i32");
            case IrInstruction.CheckedCastInterface cci ->
                    tempTypes.put(cci.dest().id(), "i32");
            case IrInstruction.MakeCallable mc ->
                    tempTypes.put(mc.dest().id(), "i32");
            case IrInstruction.InvokeCallable icv -> {
                if (icv.dest() != null) {
                    tempTypes.put(icv.dest().id(), watType(icv.dest().type()));
                }
            }
            default -> {
            }
        }
    }

    /** @return true if instruction terminates the block (branch/return) */
    private boolean emitInstruction(
            IrInstruction insn,
            StringBuilder out,
            Map<String, Integer> blockIndex,
            boolean returns
    ) {
        switch (insn) {
            case IrInstruction.JumpLabel ignored -> {
                return false;
            }
            case IrInstruction.Alloca ignored -> {
                return false;
            }
            case IrInstruction.Store st -> {
                out.append("          ").append(emitValue(st.value())).append("\n");
                out.append("          local.set $").append(slotName(st.dest().identifier())).append("\n");
                return false;
            }
            case IrInstruction.Load ld -> {
                out.append("          local.get $").append(slotName(ld.source().identifier())).append("\n");
                out.append("          local.set $t").append(ld.dest().id()).append("\n");
                return false;
            }
            case IrInstruction.StringLiteral sl -> {
                String content = sl.cString().content();
                Integer offObj = stringOffsets.get(content);
                if (offObj == null) {
                    throw new IllegalStateException("wasm: missing string data for literal: " + content);
                }
                int off = offObj;
                int len = content.getBytes(StandardCharsets.UTF_8).length;
                out.append("          i32.const ").append(off).append("\n");
                out.append("          i32.const ").append(len).append("\n");
                out.append("          call $jn_str\n");
                out.append("          local.set $t").append(sl.dest().id()).append("\n");
                return false;
            }
            case IrInstruction.Call c -> {
                emitCall(c.dest(), c.function(), c.args(), c.returnType(), out);
                return false;
            }
            case IrInstruction.Return r -> {
                if (r.value() != null) {
                    out.append("          ").append(emitValue(r.value())).append("\n");
                    if (returns) {
                        out.append("          local.set $ret\n");
                    }
                }
                out.append("          br $exit\n");
                return true;
            }
            case IrInstruction.Branch br -> {
                Integer idx = blockIndex.get(br.target());
                if (idx == null) {
                    throw new IllegalStateException("wasm: unknown branch target: " + br.target());
                }
                out.append("          i32.const ").append(idx).append("\n");
                out.append("          local.set $pc\n");
                out.append("          br $dispatch\n");
                return true;
            }
            case IrInstruction.CondBranch cb -> {
                Integer tIdx = blockIndex.get(cb.trueTarget());
                Integer fIdx = blockIndex.get(cb.falseTarget());
                if (tIdx == null || fIdx == null) {
                    throw new IllegalStateException("wasm: unknown condbranch targets: "
                            + cb.trueTarget() + " / " + cb.falseTarget());
                }
                out.append("          ").append(emitValue(cb.condition())).append("\n");
                out.append("          if\n");
                out.append("            i32.const ").append(tIdx).append("\n");
                out.append("            local.set $pc\n");
                out.append("          else\n");
                out.append("            i32.const ").append(fIdx).append("\n");
                out.append("            local.set $pc\n");
                out.append("          end\n");
                out.append("          br $dispatch\n");
                return true;
            }
            case IrInstruction.Binary b -> {
                emitBinary(b, out);
                return false;
            }
            case IrInstruction.Compare c -> {
                emitCompare(c, out);
                return false;
            }
            case IrInstruction.Cast c -> {
                out.append("          ").append(emitValue(c.source())).append("\n");
                emitCast(typeOf(c.source()), c.target(), c.mode(), out);
                out.append("          local.set $t").append(c.dest().id()).append("\n");
                return false;
            }
            case IrInstruction.UnaryNot u -> {
                out.append("          ").append(emitValue(u.operand())).append("\n");
                if (typeOf(u.operand()) == IrType.I64) {
                    out.append("          i64.eqz\n");
                } else {
                    out.append("          i32.eqz\n");
                }
                out.append("          local.set $t").append(u.dest().id()).append("\n");
                return false;
            }
            case IrInstruction.NullCheck nc -> {
                out.append("          ").append(emitValue(nc.pointer())).append("\n");
                out.append("          i32.eqz\n");
                out.append("          if\n");
                out.append("            unreachable\n"); // language NPE not wired — WASM trap
                out.append("          end\n");
                return false;
            }
            case IrInstruction.SafepointPoll ignored -> {
                return false;
            }
            case IrInstruction.VirtualCall vc -> {
                emitVirtualCall(vc, out);
                return false;
            }
            case IrInstruction.NewObject no -> {
                emitNewObject(no, out);
                return false;
            }
            case IrInstruction.InstanceOf io -> {
                out.append("          ").append(emitValue(io.object())).append("\n");
                out.append("          i32.const ").append(meta.typeId(io.targetMetaGlobal())).append("\n");
                out.append("          call $jn_instanceof\n");
                out.append("          local.set $t").append(io.dest().id()).append("\n");
                return false;
            }
            case IrInstruction.InstanceOfInterface ioi -> {
                out.append("          ").append(emitValue(ioi.object())).append("\n");
                out.append("          i32.const ").append(ioi.interfaceId()).append("\n");
                out.append("          call $jn_instanceof_iface\n");
                out.append("          local.set $t").append(ioi.dest().id()).append("\n");
                return false;
            }
            case IrInstruction.CheckedCast cc -> {
                out.append("          ").append(emitValue(cc.object())).append("\n");
                out.append("          local.set $t").append(cc.dest().id()).append("\n");
                out.append("          local.get $t").append(cc.dest().id()).append("\n");
                out.append("          i32.const ").append(meta.typeId(cc.targetMetaGlobal())).append("\n");
                out.append("          call $jn_instanceof\n");
                out.append("          i32.eqz\n");
                out.append("          if\n");
                out.append("            unreachable\n"); // ClassCast → trap (catch not on wasm yet)
                out.append("          end\n");
                return false;
            }
            case IrInstruction.CheckedCastInterface cci -> {
                out.append("          ").append(emitValue(cci.object())).append("\n");
                out.append("          local.set $t").append(cci.dest().id()).append("\n");
                out.append("          local.get $t").append(cci.dest().id()).append("\n");
                out.append("          i32.const ").append(cci.interfaceId()).append("\n");
                out.append("          call $jn_instanceof_iface\n");
                out.append("          i32.eqz\n");
                out.append("          if\n");
                out.append("            unreachable\n");
                out.append("          end\n");
                return false;
            }
            case IrInstruction.MakeCallable mc -> {
                emitMakeCallable(mc, out);
                return false;
            }
            case IrInstruction.InvokeCallable icv -> {
                emitInvokeCallable(icv, out);
                return false;
            }
            case IrInstruction.Throw t -> {
                out.append("          ").append(emitValue(t.exception())).append("\n");
                out.append("          global.set $jn_pending_exc\n");
                out.append("          unreachable\n"); // try/catch not lowered on wasm yet
                return true;
            }
            case IrInstruction.ClearException ignored -> {
                out.append("          i32.const 0\n");
                out.append("          global.set $jn_pending_exc\n");
                return false;
            }
            case IrInstruction.GcCollect ignored -> {
                // No GC — bump heap only
                return false;
            }
            case IrInstruction.ConstructorCall cc -> {
                emitCall(null, cc.mangledName(), cc.args(), IrType.VOID, out);
                return false;
            }
            case IrInstruction.LoadField lf -> {
                emitLoadField(lf, out);
                return false;
            }
            case IrInstruction.StoreField sf -> {
                emitStoreField(sf, out);
                return false;
            }
            case IrInstruction.NewArray na -> {
                emitNewArray(na, out);
                return false;
            }
            case IrInstruction.LoadElement le -> {
                emitLoadElement(le, out);
                return false;
            }
            case IrInstruction.StoreElement se -> {
                emitStoreElement(se, out);
                return false;
            }
            case IrInstruction.ArrayLength al -> {
                emitArrayLength(al, out);
                return false;
            }
            case IrInstruction.LoadStatic ls -> {
                out.append("          global.get $").append(sanitizeGlobal(ls.globalName())).append("\n");
                out.append("          local.set $t").append(ls.dest().id()).append("\n");
                return false;
            }
            case IrInstruction.StoreStatic ss -> {
                out.append("          ").append(emitValue(ss.value())).append("\n");
                out.append("          global.set $").append(sanitizeGlobal(ss.globalName())).append("\n");
                return false;
            }
            case IrInstruction.Println ignored ->
                    throw new IllegalStateException(
                            "wasm32-browser: Println is not supported (no host stdout); use Browser console APIs");
            case IrInstruction.InterfaceCall ic -> {
                emitInterfaceCall(ic, out);
                return false;
            }
            default -> throw new IllegalStateException(
                    "wasm32-browser: unsupported IR instruction (compile rejected): "
                            + insn.getClass().getSimpleName());
        }
    }

    private void emitCall(
            IrValue.Temp dest,
            String function,
            List<IrValue> args,
            IrType ret,
            StringBuilder out
    ) {
        if (isBrowserNative(function)) {
            emitBrowserNativeCall(dest, function, args, out);
            return;
        }
        if (function.equals("jnative_clinit")) {
            out.append("          call $jnative_clinit\n");
            return;
        }
        if (function.equals("jnative_exception_pending")) {
            out.append("          call $jnative_exception_pending\n");
            if (dest != null) {
                out.append("          local.set $t").append(dest.id()).append("\n");
            }
            return;
        }
        if (function.equals("jnative_string_length")) {
            for (IrValue arg : args) {
                out.append("          ").append(emitValue(arg)).append("\n");
            }
            out.append("          call $jn_string_length\n");
            if (dest != null) {
                out.append("          local.set $t").append(dest.id()).append("\n");
            }
            return;
        }
        if (function.equals("jnative_string_char_at")) {
            for (IrValue arg : args) {
                out.append("          ").append(emitValue(arg)).append("\n");
            }
            out.append("          call $jn_string_char_at\n");
            if (dest != null) {
                out.append("          local.set $t").append(dest.id()).append("\n");
            }
            return;
        }
        if (function.equals("jnative_identity_hashcode")) {
            // Identity hash = pointer bits (wasm32 i32 address)
            for (IrValue arg : args) {
                out.append("          ").append(emitValue(arg)).append("\n");
            }
            if (dest != null) {
                out.append("          local.set $t").append(dest.id()).append("\n");
            }
            return;
        }
        if (function.equals("jnative_null_check")) {
            for (IrValue arg : args) {
                out.append("          ").append(emitValue(arg)).append("\n");
            }
            out.append("          i32.eqz\n");
            out.append("          if\n");
            out.append("            unreachable\n");
            out.append("          end\n");
            return;
        }
        if (function.equals("jnative_string_concat")) {
            emitArgs(args, out);
            out.append("          call $jn_string_concat\n");
            if (dest != null) {
                out.append("          local.set $t").append(dest.id()).append("\n");
            }
            return;
        }
        if (function.equals("jnative_string_value_of_i32")) {
            emitArgs(args, out);
            out.append("          call $jn_string_value_of_i32\n");
            if (dest != null) {
                out.append("          local.set $t").append(dest.id()).append("\n");
            }
            return;
        }
        if (function.startsWith("jnative_string_value_of_")
                || function.equals("jnative_string_from_utf8")
                || function.startsWith("jnative_string_substring")) {
            out.append("          i32.const 0\n");
            out.append("          i32.const 0\n");
            out.append("          call $jn_str\n");
            if (dest != null) {
                out.append("          local.set $t").append(dest.id()).append("\n");
            }
            return;
        }
        // Soft stubs: exception bookkeeping / mutex no-ops (no host threads on wasm32-browser).
        if (function.startsWith("jnative_throwable_")
                || function.startsWith("jnative_mutex_")
                || function.equals("jnative_gc_frame_push")
                || function.equals("jnative_gc_frame_pop")
                || function.equals("jnative_gc_frame_set_root")
                || function.equals("jnative_gc_collect")
                || function.equals("jnative_exception_clear")) {
            if (dest != null) {
                String wt = watType(dest.type());
                out.append("          ").append(zeroConst(wt)).append("\n");
                out.append("          local.set $t").append(dest.id()).append("\n");
            }
            return;
        }
        if (function.equals("jnative_exception_throw")) {
            if (!args.isEmpty()) {
                out.append("          ").append(emitValue(args.get(0))).append("\n");
                out.append("          global.set $jn_pending_exc\n");
            }
            out.append("          unreachable\n");
            return;
        }
        if (function.startsWith("jnative_")) {
            throw new IllegalStateException(
                    "wasm32-browser: unsupported runtime builtin (rejected): " + function);
        }
        if (!emitSet.contains(function) && !functionsByName.containsKey(function)) {
            throw new IllegalStateException("wasm: missing callee: " + function);
        }
        for (IrValue arg : args) {
            out.append("          ").append(emitValue(arg)).append("\n");
        }
        out.append("          call $").append(sanitize(function)).append("\n");
        if (dest != null && ret != IrType.VOID) {
            out.append("          local.set $t").append(dest.id()).append("\n");
        }
    }

    private boolean isBrowserNative(String fn) {
        return fn.startsWith("_JN_BrowserNative_") || fn.startsWith("_JN_WebUiNative_")
                || fn.equals("_JN_Browser_console") || fn.equals("_JN_Browser_document")
                || fn.startsWith("_JN_Console_");
    }

    private void emitBrowserNativeCall(
            IrValue.Temp dest,
            String function,
            List<IrValue> args,
            StringBuilder out
    ) {
        if (function.equals("_JN_Browser_console") || function.equals("_JN_Browser_document")) {
            out.append("          i32.const 0\n");
            if (dest != null) {
                out.append("          local.set $t").append(dest.id()).append("\n");
            }
            return;
        }
        if (function.startsWith("_JN_Console_log")
                || function.contains("BrowserNative_consoleLog")) {
            for (IrValue arg : args) {
                out.append("          ").append(emitValue(arg)).append("\n");
            }
            out.append("          call $jn_console_log\n");
            return;
        }
        if (function.contains("BrowserNative_documentGetElementById")) {
            emitArgs(args, out);
            out.append("          call $jn_get_element_by_id\n");
            if (dest != null) {
                out.append("          local.set $t").append(dest.id()).append("\n");
            }
            return;
        }
        if (function.contains("BrowserNative_documentCreateElement")) {
            emitArgs(args, out);
            out.append("          call $jn_create_element\n");
            if (dest != null) {
                out.append("          local.set $t").append(dest.id()).append("\n");
            }
            return;
        }
        if (function.contains("BrowserNative_documentBody")) {
            out.append("          call $jn_document_body\n");
            if (dest != null) {
                out.append("          local.set $t").append(dest.id()).append("\n");
            }
            return;
        }
        if (function.contains("BrowserNative_elementSetText")) {
            emitArgs(args, out);
            out.append("          call $jn_set_text\n");
            return;
        }
        if (function.contains("BrowserNative_elementAppendChild")) {
            emitArgs(args, out);
            out.append("          call $jn_append_child\n");
            return;
        }
        if (function.contains("BrowserNative_elementAddEventListener")) {
            // (handle, typeStr, handlerCallable)
            if (args.size() < 3) {
                throw new IllegalStateException("wasm: elementAddEventListener arity");
            }
            out.append("          ").append(emitValue(args.get(2))).append("\n");
            out.append("          call $jn_register_dom_callback\n"); // -> cbId
            out.append("          local.set $__jn_tid\n");
            out.append("          ").append(emitValue(args.get(0))).append("\n");
            out.append("          ").append(emitValue(args.get(1))).append("\n");
            out.append("          local.get $__jn_tid\n");
            out.append("          call $jn_delegate_event\n");
            return;
        }
        if (function.contains("BrowserNative_inputValue")) {
            emitArgs(args, out);
            out.append("          call $jn_input_value\n");
            if (dest != null) {
                out.append("          local.set $t").append(dest.id()).append("\n");
            }
            return;
        }
        if (function.contains("BrowserNative_inputSetValue")) {
            emitArgs(args, out);
            out.append("          call $jn_input_set_value\n");
            return;
        }
        throw new IllegalStateException(
                "wasm32-browser: unsupported BrowserNative/WebUiNative call (rejected): " + function);
    }

    private void emitArgs(List<IrValue> args, StringBuilder out) {
        for (IrValue arg : args) {
            out.append("          ").append(emitValue(arg)).append("\n");
        }
    }

    private void emitNewObject(IrInstruction.NewObject no, StringBuilder out) {
        int typeId = meta.typeId(no.metadataGlobal());
        out.append("          i32.const ").append(no.sizeBytes()).append("\n");
        out.append("          call $jn_alloc_zeroed\n");
        out.append("          local.set $t").append(no.dest().id()).append("\n");
        out.append("          local.get $t").append(no.dest().id()).append("\n");
        out.append("          i32.const ").append(typeId).append("\n");
        out.append("          i32.store\n"); // typeId @0
    }

    private void emitVirtualCall(IrInstruction.VirtualCall vc, StringBuilder out) {
        // args already include receiver as first element (same as LLVM)
        List<IrValue> args = vc.args();
        String methodName = WasmMetaRegistry.methodNameFromDebug(vc.debugName());
        Map<Integer, String> byType = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : meta.allMetaTypeIds().entrySet()) {
            String metaName = e.getKey();
            String cls = metaName.startsWith("meta.") ? metaName.substring(5) : metaName;
            if (!liveClasses.isEmpty() && !liveClasses.contains(cls)) {
                continue;
            }
            String impl = meta.resolveVirtualNamed(e.getValue(), vc.slot(), methodName);
            if (impl != null && emitSet.contains(impl)) {
                byType.put(e.getValue(), impl);
            }
        }
        if (byType.isEmpty()) {
            String fallback = resolveVirtualByName(vc);
            if (fallback == null) {
                throw new IllegalStateException(
                        "wasm32-browser: unresolved VirtualCall slot=" + vc.slot()
                                + " " + vc.debugName());
            }
            emitCall(vc.dest(), fallback, args, vc.returnType(), out);
            return;
        }
        if (byType.size() == 1) {
            emitCall(vc.dest(), byType.values().iterator().next(), args, vc.returnType(), out);
            return;
        }
        // Dynamic dispatch on receiver typeId
        String retWat = vc.returnType() == IrType.VOID ? null : watType(vc.returnType());
        out.append("          ").append(emitValue(vc.receiver())).append("\n");
        out.append("          i32.load\n"); // typeId
        // Compare chain
        boolean first = true;
        for (Map.Entry<Integer, String> e : byType.entrySet()) {
            if (first) {
                out.append("          local.set $__jn_tid\n");
                first = false;
            }
            out.append("          local.get $__jn_tid\n");
            out.append("          i32.const ").append(e.getKey()).append("\n");
            out.append("          i32.eq\n");
            out.append("          if");
            if (retWat != null) {
                out.append(" (result ").append(retWat).append(")");
            }
            out.append("\n");
            for (IrValue arg : args) {
                out.append("            ").append(emitValue(arg)).append("\n");
            }
            out.append("            call $").append(sanitize(e.getValue())).append("\n");
            if (retWat == null) {
                out.append("          else\n");
            } else {
                out.append("          else\n");
            }
        }
        out.append("            unreachable\n"); // unknown type
        for (int i = 0; i < byType.size(); i++) {
            out.append("          end\n");
        }
        if (vc.returnType() != IrType.VOID) {
            if (vc.dest() != null) {
                out.append("          local.set $t").append(vc.dest().id()).append("\n");
            } else {
                out.append("          drop\n");
            }
        }
    }

    private void emitInterfaceCall(IrInstruction.InterfaceCall ic, StringBuilder out) {
        List<IrValue> args = ic.args();
        Map<Integer, String> byType = new LinkedHashMap<>();
        for (int tid : meta.implementorsOf(ic.interfaceId())) {
            String impl = meta.resolveInterface(tid, ic.interfaceId(), ic.slot());
            if (impl != null) {
                byType.put(tid, impl);
            }
        }
        if (byType.isEmpty()) {
            throw new IllegalStateException(
                    "wasm32-browser: unresolved InterfaceCall iface=" + ic.interfaceId()
                            + " slot=" + ic.slot() + " " + ic.debugName());
        }
        if (byType.size() == 1) {
            emitCall(ic.dest(), byType.values().iterator().next(), args, ic.returnType(), out);
            return;
        }
        String retWat = ic.returnType() == IrType.VOID ? null : watType(ic.returnType());
        out.append("          ").append(emitValue(ic.receiver())).append("\n");
        out.append("          i32.load\n");
        out.append("          local.set $__jn_tid\n");
        for (Map.Entry<Integer, String> e : byType.entrySet()) {
            out.append("          local.get $__jn_tid\n");
            out.append("          i32.const ").append(e.getKey()).append("\n");
            out.append("          i32.eq\n");
            out.append("          if");
            if (retWat != null) {
                out.append(" (result ").append(retWat).append(")");
            }
            out.append("\n");
            for (IrValue arg : args) {
                out.append("            ").append(emitValue(arg)).append("\n");
            }
            out.append("            call $").append(sanitize(e.getValue())).append("\n");
            out.append("          else\n");
        }
        out.append("            unreachable\n");
        for (int i = 0; i < byType.size(); i++) {
            out.append("          end\n");
        }
        if (ic.returnType() != IrType.VOID) {
            if (ic.dest() != null) {
                out.append("          local.set $t").append(ic.dest().id()).append("\n");
            } else {
                out.append("          drop\n");
            }
        }
    }

    private void emitMakeCallable(IrInstruction.MakeCallable m, StringBuilder out) {
        int typeId = meta.typeId(m.metadataGlobal());
        int codeIdx = meta.internFuncref(m.codeSymbol());
        out.append("          i32.const ").append(m.sizeBytes()).append("\n");
        out.append("          call $jn_alloc_zeroed\n");
        out.append("          local.set $t").append(m.dest().id()).append("\n");
        out.append("          local.get $t").append(m.dest().id()).append("\n");
        out.append("          i32.const ").append(typeId).append("\n");
        out.append("          i32.store\n");
        // code field is long @ CALLABLE_CODE_OFFSET — store funcref index as i64
        out.append("          local.get $t").append(m.dest().id()).append("\n");
        out.append("          i32.const ").append(WasmLayout.CALLABLE_CODE_OFFSET).append("\n");
        out.append("          i32.add\n");
        out.append("          i64.const ").append(codeIdx).append("\n");
        out.append("          i64.store\n");
        out.append("          local.get $t").append(m.dest().id()).append("\n");
        out.append("          i32.const ").append(WasmLayout.CALLABLE_ENV_OFFSET).append("\n");
        out.append("          i32.add\n");
        if (m.hasEnv()) {
            out.append("          ").append(emitValue(m.envOrNull())).append("\n");
        } else {
            out.append("          i32.const 0\n");
        }
        out.append("          i32.store\n");
    }

    private void emitInvokeCallable(IrInstruction.InvokeCallable i, StringBuilder out) {
        // Signature: (env i32, args...) -> ret
        String typeName = internIndirectType(watIndirectFuncBody(i.returnType(), i.args()));
        out.append("          ").append(emitValue(i.callable())).append("\n");
        out.append("          i32.eqz\n");
        out.append("          if\n");
        out.append("            unreachable\n");
        out.append("          end\n");
        out.append("          ").append(emitValue(i.callable())).append("\n");
        out.append("          i32.const ").append(WasmLayout.CALLABLE_ENV_OFFSET).append("\n");
        out.append("          i32.add\n");
        out.append("          i32.load\n"); // env
        for (IrValue arg : i.args()) {
            out.append("          ").append(emitValue(arg)).append("\n");
        }
        out.append("          ").append(emitValue(i.callable())).append("\n");
        out.append("          i32.const ").append(WasmLayout.CALLABLE_CODE_OFFSET).append("\n");
        out.append("          i32.add\n");
        out.append("          i64.load\n");
        out.append("          i32.wrap_i64\n"); // table index
        out.append("          call_indirect $jn_funcref (type $").append(typeName).append(")\n");
        if (i.dest() != null && i.returnType() != IrType.VOID) {
            out.append("          local.set $t").append(i.dest().id()).append("\n");
        }
    }

    private String watIndirectFuncBody(IrType ret, List<IrValue> args) {
        StringBuilder sb = new StringBuilder("(func (param i32)"); // env
        for (IrValue a : args) {
            sb.append(" (param ").append(watType(a.type())).append(")");
        }
        if (ret != IrType.VOID) {
            sb.append(" (result ").append(watType(ret)).append(")");
        }
        sb.append(")");
        return sb.toString();
    }

    /** Ensure DOM callback signature is registered even if no InvokeCallable in IR. */
    public void ensureDomCallbackType() {
        internIndirectType("(func (param i32) (param i32))");
    }

    private void emitLoadField(IrInstruction.LoadField lf, StringBuilder out) {
        ClassLayout layout = module.classLayouts().get(lf.className());
        int offset = 0;
        if (layout != null && lf.fieldIndex() < layout.fields().size()) {
            FieldLayout fl = layout.fields().get(lf.fieldIndex());
            offset = (int) fl.offset();
        }
        out.append("          ").append(emitValue(lf.receiver())).append("\n");
        out.append("          i32.const ").append(offset).append("\n");
        out.append("          i32.add\n");
        out.append("          ").append(memoryLoadOp(lf.dest().type())).append("\n");
        out.append("          local.set $t").append(lf.dest().id()).append("\n");
    }

    private void emitStoreField(IrInstruction.StoreField sf, StringBuilder out) {
        ClassLayout layout = module.classLayouts().get(sf.className());
        int offset = 0;
        if (layout != null && sf.fieldIndex() < layout.fields().size()) {
            FieldLayout fl = layout.fields().get(sf.fieldIndex());
            offset = (int) fl.offset();
        }
        out.append("          ").append(emitValue(sf.receiver())).append("\n");
        out.append("          i32.const ").append(offset).append("\n");
        out.append("          i32.add\n");
        out.append("          ").append(emitValue(sf.value())).append("\n");
        out.append("          ").append(memoryStoreOp(typeOf(sf.value()))).append("\n");
    }

    private void emitNewArray(IrInstruction.NewArray na, StringBuilder out) {
        long elemSize = na.elementSize();
        if (elemSize <= 0) {
            throw new IllegalStateException("wasm32-browser: NewArray elementSize must be > 0");
        }
        int typeId = meta.typeId(na.metadataGlobal());
        // length < 0 → trap; overflow on length*elemSize → trap
        out.append("          ").append(emitValue(na.length())).append("\n");
        out.append("          i32.const 0\n");
        out.append("          i32.lt_s\n");
        out.append("          if\n");
        out.append("            unreachable\n");
        out.append("          end\n");
        out.append("          ").append(emitValue(na.length())).append("\n");
        out.append("          i32.const ").append((int) elemSize).append("\n");
        out.append("          i32.mul\n");
        out.append("          local.set $__jn_asz\n");
        // overflow: if elemSize>0 && asz/elemSize != length
        out.append("          local.get $__jn_asz\n");
        out.append("          i32.const ").append((int) elemSize).append("\n");
        out.append("          i32.div_u\n");
        out.append("          ").append(emitValue(na.length())).append("\n");
        out.append("          i32.ne\n");
        out.append("          if\n");
        out.append("            unreachable\n");
        out.append("          end\n");
        out.append("          local.get $__jn_asz\n");
        out.append("          i32.const ").append(WasmLayout.ARRAY_DATA_OFFSET).append("\n");
        out.append("          i32.add\n");
        out.append("          call $jn_alloc_zeroed\n");
        out.append("          local.set $t").append(na.dest().id()).append("\n");
        out.append("          local.get $t").append(na.dest().id()).append("\n");
        out.append("          i32.const ").append(typeId).append("\n");
        out.append("          i32.store\n");
        out.append("          local.get $t").append(na.dest().id()).append("\n");
        out.append("          i32.const ").append(WasmLayout.ARRAY_LENGTH_OFFSET).append("\n");
        out.append("          i32.add\n");
        out.append("          ").append(emitValue(na.length())).append("\n");
        out.append("          i32.store\n");
    }

    private void emitLoadElement(IrInstruction.LoadElement le, StringBuilder out) {
        int elemSize = sizeOfIrType(le.elementType());
        emitElementAddress(le.array(), le.index(), elemSize, !le.unchecked(), out);
        out.append("          ").append(memoryLoadOp(le.elementType())).append("\n");
        out.append("          local.set $t").append(le.dest().id()).append("\n");
    }

    private void emitStoreElement(IrInstruction.StoreElement se, StringBuilder out) {
        IrType et = typeOf(se.value());
        int elemSize = sizeOfIrType(et);
        emitElementAddress(se.array(), se.index(), elemSize, true, out);
        out.append("          ").append(emitValue(se.value())).append("\n");
        out.append("          ").append(memoryStoreOp(et)).append("\n");
    }

    private void emitArrayLength(IrInstruction.ArrayLength al, StringBuilder out) {
        out.append("          ").append(emitValue(al.array())).append("\n");
        out.append("          i32.eqz\n");
        out.append("          if\n");
        out.append("            unreachable\n");
        out.append("          end\n");
        out.append("          ").append(emitValue(al.array())).append("\n");
        out.append("          i32.const ").append(WasmLayout.ARRAY_LENGTH_OFFSET).append("\n");
        out.append("          i32.add\n");
        out.append("          i32.load\n");
        out.append("          local.set $t").append(al.dest().id()).append("\n");
    }

    /** Leaves element address on the stack. Bounds / null → WASM trap (not language AIOOBE yet). */
    private void emitElementAddress(
            IrValue array, IrValue index, int elemSize, boolean checkBounds, StringBuilder out
    ) {
        out.append("          ").append(emitValue(array)).append("\n");
        out.append("          i32.eqz\n");
        out.append("          if\n");
        out.append("            unreachable\n");
        out.append("          end\n");
        if (checkBounds) {
            out.append("          ").append(emitValue(index)).append("\n");
            out.append("          i32.const 0\n");
            out.append("          i32.lt_s\n");
            out.append("          ").append(emitValue(index)).append("\n");
            out.append("          ").append(emitValue(array)).append("\n");
            out.append("          i32.const ").append(WasmLayout.ARRAY_LENGTH_OFFSET).append("\n");
            out.append("          i32.add\n");
            out.append("          i32.load\n");
            out.append("          i32.ge_s\n");
            out.append("          i32.or\n");
            out.append("          if\n");
            out.append("            unreachable\n");
            out.append("          end\n");
        }
        out.append("          ").append(emitValue(array)).append("\n");
        out.append("          i32.const ").append(WasmLayout.ARRAY_DATA_OFFSET).append("\n");
        out.append("          i32.add\n");
        out.append("          ").append(emitValue(index)).append("\n");
        out.append("          i32.const ").append(elemSize).append("\n");
        out.append("          i32.mul\n");
        out.append("          i32.add\n");
    }

    private void emitBinary(IrInstruction.Binary b, StringBuilder out) {
        IrType lt = typeOf(b.left());
        IrType rt = typeOf(b.right());
        IrType dt = b.dest().type();
        boolean fp = isFloat(lt) || isFloat(rt) || isFloat(dt);
        if (fp) {
            if (b.op() == IrInstruction.BinOp.AND || b.op() == IrInstruction.BinOp.OR
                    || b.op() == IrInstruction.BinOp.XOR || b.op() == IrInstruction.BinOp.SHL
                    || b.op() == IrInstruction.BinOp.ASHR || b.op() == IrInstruction.BinOp.LSHR) {
                throw new IllegalStateException("wasm: bitwise op not supported on float/double");
            }
            String p = (lt == IrType.DOUBLE || rt == IrType.DOUBLE || dt == IrType.DOUBLE) ? "f64" : "f32";
            out.append("          ").append(emitValue(b.left())).append("\n");
            out.append("          ").append(emitValue(b.right())).append("\n");
            // % is Java remainder (fmod), not IEEE 754 remainder — matches LLVM frem.
            String op = switch (b.op()) {
                case ADD -> p + ".add";
                case SUB -> p + ".sub";
                case MUL -> p + ".mul";
                case DIV -> p + ".div";
                case MOD -> p + ".rem";
                default -> throw new IllegalStateException("wasm: unsupported fp binop " + b.op());
            };
            out.append("          ").append(op).append("\n");
            out.append("          local.set $t").append(b.dest().id()).append("\n");
            return;
        }
        out.append("          ").append(emitValue(b.left())).append("\n");
        out.append("          ").append(emitValue(b.right())).append("\n");
        boolean wide = dt == IrType.I64 || lt == IrType.I64 || rt == IrType.I64;
        String p = wide ? "i64" : "i32";
        String op = switch (b.op()) {
            case ADD -> p + ".add";
            case SUB -> p + ".sub";
            case MUL -> p + ".mul";
            case DIV -> p + ".div_s";
            case MOD -> p + ".rem_s";
            case AND -> p + ".and";
            case OR -> p + ".or";
            case XOR -> p + ".xor";
            case SHL -> p + ".shl";
            case ASHR -> p + ".shr_s";
            case LSHR -> p + ".shr_u";
        };
        out.append("          ").append(op).append("\n");
        out.append("          local.set $t").append(b.dest().id()).append("\n");
    }

    private void emitCompare(IrInstruction.Compare c, StringBuilder out) {
        IrType lt = typeOf(c.left());
        IrType rt = typeOf(c.right());
        boolean fp = isFloat(lt) || isFloat(rt);
        out.append("          ").append(emitValue(c.left())).append("\n");
        out.append("          ").append(emitValue(c.right())).append("\n");
        if (fp) {
            String p = (lt == IrType.DOUBLE || rt == IrType.DOUBLE) ? "f64" : "f32";
            String op = switch (c.op()) {
                case EQ -> p + ".eq";
                case NE -> p + ".ne";
                case LT -> p + ".lt";
                case LE -> p + ".le";
                case GT -> p + ".gt";
                case GE -> p + ".ge";
            };
            out.append("          ").append(op).append("\n");
            out.append("          local.set $t").append(c.dest().id()).append("\n");
            return;
        }
        boolean wide = lt == IrType.I64 || rt == IrType.I64;
        String p = wide ? "i64" : "i32";
        String op = switch (c.op()) {
            case EQ -> p + ".eq";
            case NE -> p + ".ne";
            case LT -> p + ".lt_s";
            case LE -> p + ".le_s";
            case GT -> p + ".gt_s";
            case GE -> p + ".ge_s";
        };
        out.append("          ").append(op).append("\n");
        out.append("          local.set $t").append(c.dest().id()).append("\n");
    }

    private void emitCast(IrType from, IrType to, IrInstruction.CastMode mode, StringBuilder out) {
        if (from == to) {
            return;
        }
        boolean zext = mode == IrInstruction.CastMode.ZERO;

        // Float <-> float
        if (from == IrType.FLOAT && to == IrType.DOUBLE) {
            out.append("          f64.promote_f32\n");
            return;
        }
        if (from == IrType.DOUBLE && to == IrType.FLOAT) {
            out.append("          f32.demote_f64\n");
            return;
        }

        // Integer -> float/double (signed)
        if (isIntegerFamily(from) && to == IrType.FLOAT) {
            if (from == IrType.I64) {
                out.append("          f32.convert_i64_s\n");
            } else {
                if (from != IrType.I32 && isI32Family(from)) {
                    // already on i32 stack slot
                }
                out.append("          f32.convert_i32_s\n");
            }
            return;
        }
        if (isIntegerFamily(from) && to == IrType.DOUBLE) {
            if (from == IrType.I64) {
                out.append("          f64.convert_i64_s\n");
            } else {
                out.append("          f64.convert_i32_s\n");
            }
            return;
        }

        // Float/double -> integer: saturating (NaN→0, clamp); never trapping trunc
        if (from == IrType.FLOAT && to == IrType.I64) {
            out.append("          i64.trunc_sat_f32_s\n");
            return;
        }
        if (from == IrType.DOUBLE && to == IrType.I64) {
            out.append("          i64.trunc_sat_f64_s\n");
            return;
        }
        if (from == IrType.FLOAT && isI32Family(to)) {
            out.append("          i32.trunc_sat_f32_s\n");
            maskNarrowI32(to, out);
            return;
        }
        if (from == IrType.DOUBLE && isI32Family(to)) {
            out.append("          i32.trunc_sat_f64_s\n");
            maskNarrowI32(to, out);
            return;
        }

        if (isI32Family(from) && to == IrType.I64) {
            out.append(zext ? "          i64.extend_i32_u\n" : "          i64.extend_i32_s\n");
            return;
        }
        if (from == IrType.I64 && isI32Family(to)) {
            out.append("          i32.wrap_i64\n");
            maskNarrowI32(to, out);
            return;
        }
        // Same WAT width (i1/i8/i16/i32): mask on narrow/zext so char/byte semantics hold.
        if (isI32Family(from) && isI32Family(to)) {
            if (to == IrType.I16 || (zext && from == IrType.I16)) {
                out.append("          i32.const 65535\n");
                out.append("          i32.and\n");
            } else if (to == IrType.I8) {
                out.append("          i32.const 255\n");
                out.append("          i32.and\n");
            } else if (to == IrType.I1) {
                out.append("          i32.const 1\n");
                out.append("          i32.and\n");
            } else if (zext && from == IrType.I8) {
                out.append("          i32.const 255\n");
                out.append("          i32.and\n");
            }
            return;
        }
        throw new IllegalStateException("wasm: unsupported cast " + from + " -> " + to);
    }

    private static void maskNarrowI32(IrType to, StringBuilder out) {
        if (to == IrType.I16) {
            out.append("          i32.const 65535\n");
            out.append("          i32.and\n");
        } else if (to == IrType.I8) {
            out.append("          i32.const 255\n");
            out.append("          i32.and\n");
        } else if (to == IrType.I1) {
            out.append("          i32.const 1\n");
            out.append("          i32.and\n");
        }
    }

    private static boolean isI32Family(IrType t) {
        return t == IrType.I1 || t == IrType.I8 || t == IrType.I16 || t == IrType.I32
                || t == IrType.PTR || t == IrType.NATIVE_PTR;
    }

    private static boolean isIntegerFamily(IrType t) {
        return t == IrType.I1 || t == IrType.I8 || t == IrType.I16 || t == IrType.I32 || t == IrType.I64;
    }

    private static boolean isFloat(IrType t) {
        return t == IrType.FLOAT || t == IrType.DOUBLE;
    }

    private static String memoryLoadOp(IrType t) {
        return switch (t) {
            case I64 -> "i64.load";
            case FLOAT -> "f32.load";
            case DOUBLE -> "f64.load";
            default -> "i32.load";
        };
    }

    private static String memoryStoreOp(IrType t) {
        return switch (t) {
            case I64 -> "i64.store";
            case FLOAT -> "f32.store";
            case DOUBLE -> "f64.store";
            default -> "i32.store";
        };
    }

    private static int sizeOfIrType(IrType t) {
        return switch (t) {
            case I1, I8 -> 1;
            case I16 -> 2;
            case I32, FLOAT, PTR, NATIVE_PTR -> 4;
            case I64, DOUBLE -> 8;
            case VOID -> 0;
        };
    }

    private IrType typeOf(IrValue value) {
        return value.type();
    }

    private String emitValue(IrValue value) {
        return switch (value) {
            case IrValue.Temp t -> "local.get $t" + t.id();
            case IrValue.Local l -> "local.get $" + slotName(l.identifier());
            case IrValue.Const c -> {
                String n = c.name();
                if ("null".equals(n) || "nullptr".equals(n)) {
                    yield "i32.const 0";
                }
                if ("true".equals(n)) {
                    yield "i32.const 1";
                }
                if ("false".equals(n)) {
                    yield "i32.const 0";
                }
                if (c.type() == IrType.DOUBLE) {
                    yield "f64.const " + n;
                }
                if (c.type() == IrType.FLOAT) {
                    yield "f32.const " + n;
                }
                if (c.type() == IrType.I64) {
                    yield "i64.const " + n;
                }
                yield "i32.const " + n;
            }
            case IrValue.Param p -> "local.get $" + p.identifier();
            case IrValue.GlobalString ignored ->
                    throw new IllegalStateException("wasm: unexpected GlobalString as value operand");
        };
    }

    private static String watType(IrType type) {
        return switch (type) {
            case I1, I8, I16, I32 -> "i32";
            case I64 -> "i64";
            case FLOAT -> "f32";
            case DOUBLE -> "f64";
            case PTR, NATIVE_PTR -> "i32";
            case VOID -> "";
        };
    }

    private static String zeroConst(String watTy) {
        return switch (watTy) {
            case "i64" -> "i64.const 0";
            case "f32" -> "f32.const 0x0p0";
            case "f64" -> "f64.const 0x0p0";
            default -> "i32.const 0";
        };
    }

    private String slotName(String identifier) {
        if (currentParamNames.contains(identifier)) {
            return identifier + "__slot";
        }
        return identifier;
    }

    static String sanitize(String name) {
        return name.replace('.', '_').replace('<', '_').replace('>', '_').replace('@', '_');
    }

    static String sanitizeGlobal(String name) {
        return "g_" + sanitize(name);
    }
}
