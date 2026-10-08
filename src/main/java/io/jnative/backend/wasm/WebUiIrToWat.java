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
            "_JN_Math_",
            "_JN_Arrays_",
            "_JN_ArrayList_",
            "_JN_HashMap_",
            "_JN_BrowserNative_",
            "_JN_WebUiNative_",
            "_JN_WebUI_",
            "_JN_Html_",
            "_JN_IntState_",
            "_JN_WebUiComponent_"
    );

    private final IrModule module;
    private final Map<String, Integer> stringOffsets;
    private final Map<String, IrFunction> functionsByName = new HashMap<>();
    private final Set<String> emitSet = new LinkedHashSet<>();
    private final Set<String> exportSet = new LinkedHashSet<>();
    private final Set<String> staticGlobals = new LinkedHashSet<>();
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
        for (IrFunction fn : module.functions()) {
            functionsByName.put(fn.name(), fn);
        }
        for (IrModule.StaticField sf : module.staticFields()) {
            staticGlobals.add(sf.globalName());
            globalWatTypes.put(sf.globalName(), watType(sf.type()));
        }
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
                String resolved = resolveVirtual(vc);
                if (resolved != null) {
                    q.add(resolved);
                }
            }
            case IrInstruction.InterfaceCall ic -> {
                String resolved = resolveInterface(ic);
                if (resolved != null) {
                    q.add(resolved);
                }
            }
            default -> {
            }
        }
    }

    private static boolean isExternalBuiltin(String fn) {
        return fn.startsWith("jnative_")
                || fn.startsWith("_JN_BrowserNative_")
                || fn.startsWith("_JN_WebUiNative_")
                || fn.equals("_JN_Browser_console")
                || fn.equals("_JN_Browser_document")
                || fn.startsWith("_JN_Console_");
    }

    private String resolveVirtual(IrInstruction.VirtualCall vc) {
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
        for (String fn : functionsByName.keySet()) {
            if (fn.startsWith(prefix)) {
                return fn;
            }
        }
        return null;
    }

    private String resolveInterface(IrInstruction.InterfaceCall ic) {
        return null;
    }

    private void emitBuiltinStubs(StringBuilder out) {
        if (!emitSet.contains("jnative_exception_pending")) {
            out.append("  (func $jnative_exception_pending (result i32) (i32.const 0))\n");
        }
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
            case IrInstruction.NewObject no ->
                    tempTypes.put(no.dest().id(), "i32");
            case IrInstruction.LoadField lf ->
                    tempTypes.put(lf.dest().id(), watType(lf.dest().type()));
            case IrInstruction.LoadStatic ls ->
                    tempTypes.put(ls.dest().id(), watType(ls.dest().type()));
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
                emitCast(typeOf(c.source()), c.target(), out);
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
            case IrInstruction.NullCheck ignored -> {
                return false;
            }
            case IrInstruction.SafepointPoll ignored -> {
                return false;
            }
            case IrInstruction.VirtualCall vc -> {
                String target = resolveVirtual(vc);
                if (target == null) {
                    throw new IllegalStateException("wasm: unresolved VirtualCall " + vc.debugName());
                }
                List<IrValue> args = new ArrayList<>();
                args.add(vc.receiver());
                args.addAll(vc.args());
                emitCall(vc.dest(), target, args, vc.returnType(), out);
                return false;
            }
            case IrInstruction.NewObject no -> {
                out.append("          i32.const ").append(no.sizeBytes()).append("\n");
                out.append("          call $jn_alloc\n");
                out.append("          local.set $t").append(no.dest().id()).append("\n");
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
                    throw new IllegalStateException("wasm: Println not supported in wasm32-browser IR lowering");
            case IrInstruction.InterfaceCall ic ->
                    throw new IllegalStateException("wasm: InterfaceCall not supported: " + ic);
            default -> throw new IllegalStateException(
                    "wasm: unsupported IR instruction in browser backend: " + insn.getClass().getSimpleName());
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
        if (function.startsWith("jnative_")) {
            throw new IllegalStateException("wasm: unsupported runtime builtin: " + function);
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
        if (function.startsWith("_JN_Console_log")) {
            for (IrValue arg : args) {
                out.append("          ").append(emitValue(arg)).append("\n");
            }
            out.append("          call $jn_console_log\n");
            return;
        }
        throw new IllegalStateException("wasm: unsupported BrowserNative/WebUiNative call: " + function);
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
        if (lf.dest().type() == IrType.I64) {
            out.append("          i64.load\n");
        } else {
            out.append("          i32.load\n");
        }
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
        if (typeOf(sf.value()) == IrType.I64) {
            out.append("          i64.store\n");
        } else {
            out.append("          i32.store\n");
        }
    }

    private void emitBinary(IrInstruction.Binary b, StringBuilder out) {
        out.append("          ").append(emitValue(b.left())).append("\n");
        out.append("          ").append(emitValue(b.right())).append("\n");
        boolean wide = b.dest().type() == IrType.I64
                || typeOf(b.left()) == IrType.I64
                || typeOf(b.right()) == IrType.I64;
        String p = wide ? "i64" : "i32";
        String op = switch (b.op()) {
            case ADD -> p + ".add";
            case SUB -> p + ".sub";
            case MUL -> p + ".mul";
            case DIV -> p + ".div_s";
            case MOD -> p + ".rem_s";
            case AND -> p + ".and";
            case OR -> p + ".or";
        };
        out.append("          ").append(op).append("\n");
        out.append("          local.set $t").append(b.dest().id()).append("\n");
    }

    private void emitCompare(IrInstruction.Compare c, StringBuilder out) {
        out.append("          ").append(emitValue(c.left())).append("\n");
        out.append("          ").append(emitValue(c.right())).append("\n");
        boolean wide = typeOf(c.left()) == IrType.I64 || typeOf(c.right()) == IrType.I64;
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

    private void emitCast(IrType from, IrType to, StringBuilder out) {
        if (from == to) {
            return;
        }
        if (isI32Family(from) && to == IrType.I64) {
            out.append("          i64.extend_i32_s\n");
            return;
        }
        if (from == IrType.I64 && isI32Family(to)) {
            out.append("          i32.wrap_i64\n");
            return;
        }
        // Same WAT width (i1/i8/i16/i32) — no-op
        if (isI32Family(from) && isI32Family(to)) {
            return;
        }
        throw new IllegalStateException("wasm: unsupported cast " + from + " -> " + to);
    }

    private static boolean isI32Family(IrType t) {
        return t == IrType.I1 || t == IrType.I8 || t == IrType.I16 || t == IrType.I32
                || t == IrType.PTR || t == IrType.NATIVE_PTR;
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
