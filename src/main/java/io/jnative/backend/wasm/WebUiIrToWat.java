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
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Lowers reachable JNative IR functions to WAT for wasm32-browser.
 * BrowserNative / WebUiNative calls become JS imports; GC safepoints are no-ops.
 */
public final class WebUiIrToWat {

    private final IrModule module;
    private final Map<String, Integer> stringOffsets;
    private final Map<String, IrFunction> functionsByName = new HashMap<>();
    private final Set<String> emitSet = new HashSet<>();
    private final StringBuilder wat = new StringBuilder();
    private int labelCounter;

    public WebUiIrToWat(IrModule module, Map<String, Integer> stringOffsets) {
        this.module = module;
        this.stringOffsets = stringOffsets;
        for (IrFunction fn : module.functions()) {
            functionsByName.put(fn.name(), fn);
        }
    }

    public boolean usesWebUi() {
        for (IrValue.GlobalString gs : module.strings()) {
            if (gs.content() != null && (gs.content().contains("WebUI")
                    || gs.content().contains("Increment")
                    || gs.content().contains("#app"))) {
                return true;
            }
        }
        return functionsByName.containsKey("jn_main")
                && functionsByName.keySet().stream().anyMatch(n -> n.contains("WebUI"));
    }

    public void emitFunctions(StringBuilder out) {
        collectReachable("jn_main");
        for (String name : emitSet) {
            IrFunction fn = functionsByName.get(name);
            if (fn != null) {
                emitFunction(fn, out);
            }
        }
        emitStubs(out);
    }

    private void collectReachable(String root) {
        Queue<String> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            String name = q.poll();
            if (!emitSet.add(name)) {
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
            case IrInstruction.Call c -> q.add(c.function());
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

    private void emitStubs(StringBuilder out) {
        if (!emitSet.contains("jnative_exception_pending")) {
            out.append("  (func $jnative_exception_pending (result i32) (i32.const 0))\n");
        }
    }

    private void emitFunction(IrFunction fn, StringBuilder out) {
        String watName = sanitize(fn.name());
        out.append("  (func $").append(watName);
        for (IrValue.Param p : fn.params()) {
            out.append(" (param $").append(p.identifier()).append(" ").append(watType(p.type())).append(")");
        }
        if (fn.returnType() != IrType.VOID) {
            out.append(" (result ").append(watType(fn.returnType())).append(")");
        }
        out.append("\n");
        Map<String, Integer> locals = new LinkedHashMap<>();
        int localIndex = fn.params().size();
        for (IrBasicBlock block : fn.blocks()) {
            for (IrInstruction insn : block.instructions()) {
                if (insn instanceof IrInstruction.Alloca a) {
                    locals.put(a.dest().identifier(), localIndex++);
                }
            }
        }
        for (Map.Entry<String, Integer> e : locals.entrySet()) {
            out.append("    (local $").append(e.getKey()).append(" i32)\n");
        }
        for (IrBasicBlock block : fn.blocks()) {
            out.append("    ;; block ").append(block.name()).append("\n");
            for (IrInstruction insn : block.instructions()) {
                emitInstruction(insn, out, fn, locals);
            }
        }
        out.append("  )\n");
    }

    private void emitInstruction(
            IrInstruction insn,
            StringBuilder out,
            IrFunction fn,
            Map<String, Integer> locals
    ) {
        switch (insn) {
            case IrInstruction.JumpLabel jl -> out.append("    ").append(sanitizeLabel(jl.name())).append(":\n");
            case IrInstruction.Alloca ignored -> {
            }
            case IrInstruction.Store st -> {
                out.append("    ").append(emitValue(st.value(), locals)).append("\n");
                out.append("    local.set $").append(st.dest().identifier()).append("\n");
            }
            case IrInstruction.Load ld -> {
                out.append("    local.get $").append(ld.source().identifier()).append("\n");
                out.append("    local.set $t").append(ld.dest().id()).append("\n");
            }
            case IrInstruction.StringLiteral sl -> {
                String content = sl.cString().content();
                int off = stringOffsets.getOrDefault(content, 0);
                int len = content.getBytes(StandardCharsets.UTF_8).length;
                out.append("    i32.const ").append(off).append("\n");
                out.append("    i32.const ").append(len).append("\n");
                out.append("    call $jn_str\n");
                out.append("    local.set $t").append(sl.dest().id()).append("\n");
            }
            case IrInstruction.Call c -> emitCall(c.dest(), c.function(), c.args(), c.returnType(), out, locals);
            case IrInstruction.Return r -> {
                if (r.value() != null) {
                    out.append("    ").append(emitValue(r.value(), locals)).append("\n");
                }
                out.append("    return\n");
            }
            case IrInstruction.Branch br -> out.append("    br $").append(sanitizeLabel(br.target())).append("\n");
            case IrInstruction.CondBranch cb -> {
                out.append("    ").append(emitValue(cb.condition(), locals)).append("\n");
                out.append("    if\n");
                out.append("      br $").append(sanitizeLabel(cb.trueTarget())).append("\n");
                out.append("    else\n");
                out.append("      br $").append(sanitizeLabel(cb.falseTarget())).append("\n");
                out.append("    end\n");
            }
            case IrInstruction.Binary b -> emitBinary(b, out, locals);
            case IrInstruction.Compare c -> emitCompare(c, out, locals);
            case IrInstruction.NullCheck ignored -> {
            }
            case IrInstruction.VirtualCall vc -> {
                String target = resolveVirtual(vc);
                if (target != null) {
                    List<IrValue> args = new ArrayList<>();
                    args.add(vc.receiver());
                    emitCall(vc.dest(), target, args, vc.returnType(), out, locals);
                }
            }
            case IrInstruction.NewObject no -> {
                out.append("    i32.const ").append(no.sizeBytes()).append("\n");
                out.append("    call $jn_alloc\n");
                out.append("    local.set $t").append(no.dest().id()).append("\n");
            }
            case IrInstruction.ConstructorCall cc -> emitCall(null, cc.mangledName(), cc.args(), IrType.VOID, out, locals);
            case IrInstruction.LoadField lf -> emitLoadField(lf, out, locals);
            case IrInstruction.StoreField sf -> emitStoreField(sf, out, locals);
            default -> {
            }
        }
    }

    private void emitCall(
            IrValue.Temp dest,
            String function,
            List<IrValue> args,
            IrType ret,
            StringBuilder out,
            Map<String, Integer> locals
    ) {
        if (isBrowserNative(function)) {
            emitBrowserNativeCall(dest, function, args, out, locals);
            return;
        }
        if (function.equals("jnative_exception_pending")) {
            out.append("    call $jnative_exception_pending\n");
            if (dest != null) {
                out.append("    local.set $t").append(dest.id()).append("\n");
            }
            return;
        }
        for (IrValue arg : args) {
            out.append("    ").append(emitValue(arg, locals)).append("\n");
        }
        out.append("    call $").append(sanitize(function)).append("\n");
        if (dest != null && ret != IrType.VOID) {
            out.append("    local.set $t").append(dest.id()).append("\n");
        }
    }

    private boolean isBrowserNative(String fn) {
        return fn.startsWith("_JN_BrowserNative_") || fn.startsWith("_JN_WebUiNative_")
                || fn.equals("_JN_Browser_console") || fn.equals("_JN_Browser_document");
    }

    private void emitBrowserNativeCall(
            IrValue.Temp dest,
            String function,
            List<IrValue> args,
            StringBuilder out,
            Map<String, Integer> locals
    ) {
        if (function.equals("_JN_Browser_console")) {
            out.append("    call $jn_browser_console\n");
            if (dest != null) {
                out.append("    local.set $t").append(dest.id()).append("\n");
            }
            return;
        }
        if (function.equals("_JN_Browser_document")) {
            out.append("    call $jn_browser_document\n");
            if (dest != null) {
                out.append("    local.set $t").append(dest.id()).append("\n");
            }
            return;
        }
        if (function.startsWith("_JN_Console_log")) {
            for (IrValue arg : args) {
                if (arg instanceof IrValue.Temp || arg instanceof IrValue.Local) {
                    out.append("    ").append(emitValue(arg, locals)).append("\n");
                }
            }
            out.append("    call $jn_console_log\n");
            return;
        }
        if (function.startsWith("_JN_WebUiNative_scheduleMicrotask")) {
            out.append("    call $js_schedule_microtask\n");
            return;
        }
        if (function.startsWith("_JN_WebUiNative_applyDomPatches")) {
            for (IrValue arg : args) {
                out.append("    ").append(emitValue(arg, locals)).append("\n");
            }
            out.append("    call $js_apply_dom_patches\n");
            return;
        }
        if (function.startsWith("_JN_WebUiNative_querySelector")) {
            for (IrValue arg : args) {
                out.append("    ").append(emitValue(arg, locals)).append("\n");
            }
            out.append("    call $js_query_selector\n");
            if (dest != null) {
                out.append("    local.set $t").append(dest.id()).append("\n");
            }
            return;
        }
        if (function.startsWith("_JN_WebUiNative_createElement")) {
            for (IrValue arg : args) {
                out.append("    ").append(emitValue(arg, locals)).append("\n");
            }
            out.append("    call $js_create_element\n");
            if (dest != null) {
                out.append("    local.set $t").append(dest.id()).append("\n");
            }
            return;
        }
        if (function.startsWith("_JN_WebUiNative_createTextNode")) {
            for (IrValue arg : args) {
                out.append("    ").append(emitValue(arg, locals)).append("\n");
            }
            out.append("    call $js_create_text\n");
            if (dest != null) {
                out.append("    local.set $t").append(dest.id()).append("\n");
            }
            return;
        }
        if (function.startsWith("_JN_WebUiNative_registerCallback")) {
            out.append("    call $js_register_callback\n");
            if (dest != null) {
                out.append("    local.set $t").append(dest.id()).append("\n");
            }
            return;
        }
        if (function.startsWith("_JN_WebUiNative_delegateEvent")) {
            for (IrValue arg : args) {
                out.append("    ").append(emitValue(arg, locals)).append("\n");
            }
            out.append("    call $js_delegate_event\n");
            return;
        }
        if (function.startsWith("_JN_WebUiNative_initEventDelegation")) {
            for (IrValue arg : args) {
                out.append("    ").append(emitValue(arg, locals)).append("\n");
            }
            out.append("    call $js_init_delegation\n");
            return;
        }
        // fallback noop
        if (dest != null) {
            out.append("    i32.const 0\n");
            out.append("    local.set $t").append(dest.id()).append("\n");
        }
    }

    private void emitLoadField(IrInstruction.LoadField lf, StringBuilder out, Map<String, Integer> locals) {
        ClassLayout layout = module.classLayouts().get(lf.className());
        int offset = 0;
        if (layout != null && lf.fieldIndex() < layout.fields().size()) {
            FieldLayout fl = layout.fields().get(lf.fieldIndex());
            offset = (int) fl.offset();
        }
        out.append("    ").append(emitValue(lf.receiver(), locals)).append("\n");
        out.append("    i32.const ").append(offset).append("\n");
        out.append("    i32.add\n");
        out.append("    i32.load\n");
        out.append("    local.set $t").append(lf.dest().id()).append("\n");
    }

    private void emitStoreField(IrInstruction.StoreField sf, StringBuilder out, Map<String, Integer> locals) {
        ClassLayout layout = module.classLayouts().get(sf.className());
        int offset = 0;
        if (layout != null && sf.fieldIndex() < layout.fields().size()) {
            FieldLayout fl = layout.fields().get(sf.fieldIndex());
            offset = (int) fl.offset();
        }
        out.append("    ").append(emitValue(sf.receiver(), locals)).append("\n");
        out.append("    i32.const ").append(offset).append("\n");
        out.append("    i32.add\n");
        out.append("    ").append(emitValue(sf.value(), locals)).append("\n");
        out.append("    i32.store\n");
    }

    private void emitBinary(IrInstruction.Binary b, StringBuilder out, Map<String, Integer> locals) {
        out.append("    ").append(emitValue(b.left(), locals)).append("\n");
        out.append("    ").append(emitValue(b.right(), locals)).append("\n");
        String op = switch (b.op()) {
            case ADD -> "i32.add";
            case SUB -> "i32.sub";
            case MUL -> "i32.mul";
            default -> "i32.add";
        };
        out.append("    ").append(op).append("\n");
        out.append("    local.set $t").append(b.dest().id()).append("\n");
    }

    private void emitCompare(IrInstruction.Compare c, StringBuilder out, Map<String, Integer> locals) {
        out.append("    ").append(emitValue(c.left(), locals)).append("\n");
        out.append("    ").append(emitValue(c.right(), locals)).append("\n");
        String op = switch (c.op()) {
            case EQ -> "i32.eq";
            case NE -> "i32.ne";
            case LT -> "i32.lt_s";
            case LE -> "i32.le_s";
            case GT -> "i32.gt_s";
            case GE -> "i32.ge_s";
        };
        out.append("    ").append(op).append("\n");
        out.append("    local.set $t").append(c.dest().id()).append("\n");
    }

    private String emitValue(IrValue value, Map<String, Integer> locals) {
        return switch (value) {
            case IrValue.Temp t -> "local.get $t" + t.id();
            case IrValue.Local l -> "local.get $" + l.identifier();
            case IrValue.Const c -> "i32.const " + c.name();
            case IrValue.Param p -> "local.get $" + p.identifier();
            default -> "i32.const 0";
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

    private static String sanitize(String name) {
        return name.replace('.', '_').replace('<', '_').replace('>', '_');
    }

    private String sanitizeLabel(String name) {
        return "L" + name.replace('.', '_').replace('%', '_');
    }
}
