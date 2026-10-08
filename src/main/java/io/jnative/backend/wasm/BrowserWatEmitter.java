package io.jnative.backend.wasm;

import io.jnative.ir.IrBasicBlock;
import io.jnative.ir.IrFunction;
import io.jnative.ir.IrInstruction;
import io.jnative.ir.IrModule;
import io.jnative.ir.IrValue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Emits WAT for wasm32-browser from real JNative IR (via {@link WebUiIrToWat}).
 * No fixed-template programs keyed on class names or string literals.
 */
public final class BrowserWatEmitter {

    private final Map<String, Integer> stringData = new LinkedHashMap<>();
    private int dataCursor = 1024;

    public String emit(IrModule ir) {
        collectStrings(ir);
        WebUiIrToWat lowering = new WebUiIrToWat(ir, stringData);
        lowering.collect();

        StringBuilder wat = new StringBuilder();
        wat.append("(module\n");
        emitImports(wat);
        wat.append("  (memory (export \"memory\") 256 1024)\n");
        wat.append("  (global $heap (mut i32) (i32.const ").append(dataCursor + 64).append("))\n");
        for (String g : lowering.staticGlobals()) {
            String gt = lowering.globalWatType(g);
            String zero = "i64".equals(gt) ? "i64.const 0" : "i32.const 0";
            wat.append("  (global $").append(WebUiIrToWat.sanitizeGlobal(g))
                    .append(" (mut ").append(gt).append(") (").append(zero).append("))\n");
        }
        emitData(wat);
        emitRuntime(wat);
        lowering.emitAll(wat);
        wat.append("  (func (export \"jnative_start\")\n");
        if (lowering.emittedFunctions().contains("jn_main")) {
            wat.append("    i32.const 0\n");
            wat.append("    call $jn_main\n");
        }
        wat.append("  )\n");
        wat.append("  (func (export \"jn_bump\") (param $n i32) (result i32)\n");
        wat.append("    local.get $n\n");
        wat.append("    call $jn_alloc\n");
        wat.append("  )\n");
        wat.append(")\n");
        return wat.toString();
    }

    private void collectStrings(IrModule ir) {
        for (IrValue.GlobalString gs : ir.strings()) {
            if (gs != null && gs.content() != null) {
                intern(gs.content());
            }
        }
        for (IrFunction fn : ir.functions()) {
            for (IrBasicBlock block : fn.blocks()) {
                for (IrInstruction insn : block.instructions()) {
                    if (insn instanceof IrInstruction.StringLiteral sl && sl.cString() != null) {
                        intern(sl.cString().content());
                    }
                }
            }
        }
        intern("");
    }

    private int intern(String s) {
        if (s == null) {
            s = "";
        }
        Integer existing = stringData.get(s);
        if (existing != null) {
            return existing;
        }
        int off = dataCursor;
        byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
        stringData.put(s, off);
        dataCursor += Math.max(1, utf8.length);
        return off;
    }

    private void emitImports(StringBuilder wat) {
        wat.append("  (import \"jnative\" \"console_log\" (func $js_console_log (param i32 i32)))\n");
        wat.append("  (import \"jnative\" \"document_get_element_by_id\" (func $js_get_by_id (param i32 i32) (result i32)))\n");
        wat.append("  (import \"jnative\" \"element_set_text\" (func $js_set_text (param i32 i32 i32)))\n");
        wat.append("  (import \"jnative\" \"document_create_element\" (func $js_create_element (param i32 i32) (result i32)))\n");
        wat.append("  (import \"jnative\" \"element_append_child\" (func $js_append_child (param i32 i32)))\n");
        wat.append("  (import \"jnative\" \"create_text\" (func $js_create_text (param i32 i32) (result i32)))\n");
        wat.append("  (import \"jnative\" \"query_selector\" (func $js_query_selector (param i32 i32) (result i32)))\n");
        wat.append("  (import \"jnative\" \"apply_dom_patches\" (func $js_apply_dom_patches (param i32 i32)))\n");
        wat.append("  (import \"jnative\" \"schedule_microtask\" (func $js_schedule_microtask))\n");
        wat.append("  (import \"jnative\" \"delegate_event\" (func $js_delegate_event (param i32 i32 i32 i32)))\n");
        wat.append("  (import \"jnative\" \"init_delegation\" (func $js_init_delegation (param i32)))\n");
        wat.append("  (import \"jnative\" \"register_callback\" (func $js_register_callback (param i32) (result i32)))\n");
        wat.append("  (import \"jnative\" \"set_count_label\" (func $js_set_count_label (param i32 i32)))\n");
    }

    private void emitData(StringBuilder wat) {
        for (Map.Entry<String, Integer> e : stringData.entrySet()) {
            byte[] utf8 = e.getKey().getBytes(StandardCharsets.UTF_8);
            StringBuilder esc = new StringBuilder();
            for (byte b : utf8) {
                esc.append(String.format("\\%02x", b & 0xff));
            }
            wat.append("  (data (i32.const ").append(e.getValue()).append(") \"").append(esc).append("\")\n");
        }
    }

    private void emitRuntime(StringBuilder wat) {
        wat.append("""
                  (func $jn_alloc (param $n i32) (result i32)
                    (local $p i32)
                    global.get $heap
                    local.set $p
                    global.get $heap
                    local.get $n
                    i32.const 7
                    i32.add
                    i32.const -8
                    i32.and
                    i32.add
                    global.set $heap
                    local.get $p
                  )
                  (func $jn_str (param $off i32) (param $len i32) (result i32)
                    (local $p i32) (local $i i32)
                    local.get $len
                    i32.const 4
                    i32.add
                    call $jn_alloc
                    local.set $p
                    local.get $p
                    local.get $len
                    i32.store
                    i32.const 0
                    local.set $i
                    (block $done
                      (loop $copy
                        local.get $i
                        local.get $len
                        i32.ge_u
                        br_if $done
                        local.get $p
                        i32.const 4
                        i32.add
                        local.get $i
                        i32.add
                        local.get $off
                        local.get $i
                        i32.add
                        i32.load8_u
                        i32.store8
                        local.get $i
                        i32.const 1
                        i32.add
                        local.set $i
                        br $copy
                      )
                    )
                    local.get $p
                  )
                  (func $jn_str_len (param $s i32) (result i32)
                    local.get $s
                    i32.eqz
                    if (result i32)
                      i32.const 0
                    else
                      local.get $s
                      i32.load
                    end
                  )
                  (func $jn_str_data (param $s i32) (result i32)
                    local.get $s
                    i32.const 4
                    i32.add
                  )
                  ;; Browser string layout: [i32 len][bytes...] (matches jn_str)
                  (func $jn_string_length (param $s i32) (result i32)
                    local.get $s
                    call $jn_str_len
                  )
                  (func $jn_string_char_at (param $s i32) (param $index i32) (result i32)
                    (local $len i32) (local $p i32)
                    local.get $s
                    i32.eqz
                    if
                      i32.const 0
                      return
                    end
                    local.get $s
                    i32.load
                    local.set $len
                    local.get $index
                    i32.const 0
                    i32.lt_s
                    if
                      i32.const 0
                      return
                    end
                    local.get $index
                    local.get $len
                    i32.ge_s
                    if
                      i32.const 0
                      return
                    end
                    local.get $s
                    i32.const 4
                    i32.add
                    local.get $index
                    i32.add
                    i32.load8_u
                    return
                  )
                  (func $jn_console_log (param $s i32)
                    local.get $s
                    call $jn_str_data
                    local.get $s
                    call $jn_str_len
                    call $js_console_log
                  )
                  (func $jn_get_element_by_id (param $s i32) (result i32)
                    local.get $s
                    call $jn_str_data
                    local.get $s
                    call $jn_str_len
                    call $js_get_by_id
                  )
                  (func $jn_set_text (param $h i32) (param $s i32)
                    local.get $h
                    local.get $s
                    call $jn_str_data
                    local.get $s
                    call $jn_str_len
                    call $js_set_text
                  )
                """);
    }
}
